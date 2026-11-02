package com.smartrecruit.application;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.ApplicantFixtures;
import com.smartrecruit.support.CandidateSession;
import com.smartrecruit.support.JobFixtures;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-30 à US-34 : les candidatures vues et traitées par le recruteur, de bout en bout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class RecruiterApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private ApplicantFixtures applicants;

    @Autowired
    private JdbcClient jdbc;

    private Session admin;
    private Session recruiter;
    private UUID jobId;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
        recruiter = staff.recruiterOfNewCompany(admin);
        jobId = jobs.published(recruiter, "Développeur backend Java", "Casablanca", null);
    }

    // ---------------------------------------------------------------- US-30 liste

    @Test
    void listShowsOnlyMyCompanyNewestFirst() throws Exception {
        UUID first = applicants.apply(applicants.readyCandidate("Salma", "Idrissi", "java"), jobId);
        UUID second = applicants.apply(applicants.readyCandidate("Karim", "Bennani", "java"), jobId);
        Session competitor = staff.recruiterOfNewCompany(admin);
        UUID otherJob = jobs.published(competitor, "Data analyst", "Rabat", null);
        applicants.apply(applicants.readyCandidate("Nadia", "Alaoui", "python"), otherJob);

        mockMvc.perform(recruiter.auth(get("/api/v1/applications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(contains(second.toString(), first.toString())))
                .andExpect(jsonPath("$.content[0].candidate.fullName").value("Karim Bennani"))
                .andExpect(jsonPath("$.content[0].candidate.headline").value("Développeuse Java / Angular"))
                .andExpect(jsonPath("$.content[0].job.title").value("Développeur backend Java"))
                .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));
    }

    @Test
    void filtersAreCombined() throws Exception {
        UUID otherJob = jobs.published(recruiter, "Développeur Angular", "Rabat", null);
        UUID salma = applicants.apply(applicants.readyCandidate("Salma", "Idrissi", "java", "docker"), jobId);
        UUID karim = applicants.apply(applicants.readyCandidate("Karim", "Bennani", "java"), jobId);
        UUID nadia = applicants.apply(applicants.readyCandidate("Nadia", "Élalami", "angular"), otherJob);
        move(karim, "SCREENING", null);

        assertIds(list("jobOfferId", jobId.toString()), salma, karim);
        assertIds(list("status", "SCREENING,SHORTLISTED"), karim);
        assertIds(list("skillId", applicants.skillId("java") + "," + applicants.skillId("docker")), salma);
        // Nom sans casse ni accents, dans les deux ordres
        assertIds(list("candidateName", "elalami"), nadia);
        assertIds(list("candidateName", "bennani karim"), karim);
        // Période : jours calendaires, « au » inclus
        String today = LocalDate.now().toString();
        assertIds(list("submittedFrom", today, "submittedTo", today, "jobOfferId", jobId.toString(),
                "status", "SUBMITTED"), salma);
        assertIds(list("submittedTo", LocalDate.now().minusDays(1).toString()));
        // Aucun matching encore : un score minimum exclut tout
        assertIds(list("minScore", "50"));
        list("minScore", "101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minScore"));
    }

    // ---------------------------------------------------------------- US-31 dossier

    @Test
    void dossierShowsTheProfileTheResumeSentAndTheAllowedTransitions() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID applicationId = applicants.apply(candidate, jobId);
        applicants.uploadResume(candidate, "cv-v2.pdf");

        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.allowedTransitions").value(contains("SCREENING", "REJECTED")))
                .andExpect(jsonPath("$.candidate.fullName").value("Salma Idrissi"))
                .andExpect(jsonPath("$.candidate.email").value(candidate.email()))
                .andExpect(jsonPath("$.candidate.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.resume.originalFilename").value("cv-salma.pdf"))
                .andExpect(jsonPath("$.rejection").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));

        // RG-12 : la version envoyée, pas le nouveau CV ; téléchargement forcé et audité
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/resume")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("attachment"),
                                org.hamcrest.Matchers.endsWith("cv-salma.pdf"))))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        assertThat(jdbc.sql("""
                        select count(*) from audit_log where action = 'RESUME_DOWNLOADED'
                        and actor_id = :recruiter and details ->> 'applicationId' = :application""")
                .param("recruiter", recruiter.userId()).param("application", applicationId.toString())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void applicationOfAnotherCompanyIsNotFound() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);
        Session competitor = staff.recruiterOfNewCompany(admin);
        String base = "/api/v1/applications/" + applicationId;

        mockMvc.perform(competitor.auth(get(base))).andExpect(status().isNotFound());
        mockMvc.perform(competitor.auth(get(base + "/history"))).andExpect(status().isNotFound());
        mockMvc.perform(competitor.auth(get(base + "/resume"))).andExpect(status().isNotFound());
        mockMvc.perform(competitor.auth(patch(base + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"SCREENING\",\"version\":0}"))
                .andExpect(status().isNotFound());
        assertThat(applicants.version(applicationId)).isZero();
    }

    // ---------------------------------------------------------------- US-32 workflow, US-34 historique

    @Test
    void applicationMovesForwardUntilAnInterviewAndEachStepIsInTheHistory() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);
        move(applicationId, "SCREENING", null);
        move(applicationId, "SHORTLISTED", null);
        move(applicationId, "INTERVIEW", null)
                // RG-33 : pas de proposition proposée sans entretien terminé
                .andExpect(jsonPath("$.allowedTransitions").value(contains("REJECTED")));

        patchStatus(applicationId, "OFFER", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_REQUIRES_COMPLETED_INTERVIEW"));

        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].toStatus").value(contains("INTERVIEW", "SHORTLISTED", "SCREENING", "SUBMITTED")))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].fromStatus").value("SHORTLISTED"))
                .andExpect(jsonPath("$[0].actor.name").value("Youssef Alami"))
                .andExpect(jsonPath("$[3].type").value("CREATED"))
                .andExpect(jsonPath("$[3].actor.name").value("Salma Idrissi"));
        // Compteurs de l'offre (US-24) et retour en brouillon impossible (US-23)
        mockMvc.perform(recruiter.auth(get("/api/v1/company/jobs/" + jobId)))
                .andExpect(jsonPath("$.applicationCounts.INTERVIEW").value(1));
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs/" + jobId + "/unpublish")))
                .andExpect(status().isConflict());
    }

    @Test
    void transitionsOutsideTheTableAreRefusedAndNothingChanges() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);

        for (String target : List.of("HIRED", "INTERVIEW", "WITHDRAWN", "SUBMITTED")) {
            patchStatus(applicationId, target, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }
        assertThat(applicants.version(applicationId)).isZero();
        assertThat(historyCount(applicationId)).isEqualTo(1);
    }

    /** A2 : deux recruteurs modifient la même candidature ; le second a lu une version périmée. */
    @Test
    void staleVersionIsAConflict() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);
        Session colleague = staff.recruiter(admin, recruiter.companyId());
        move(applicationId, "SCREENING", null);

        mockMvc.perform(colleague.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REJECTED\",\"rejectionReason\":\"OTHER\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId)))
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ---------------------------------------------------------------- US-33 rejet

    @Test
    void rejectionNeedsAReasonIsKeptInternallyAndIsFinal() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);

        patchStatus(applicationId, "REJECTED", null)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));

        move(applicationId, "REJECTED", ",\"rejectionReason\":\"PROFILE_MISMATCH\",\"comment\":\"Hors sujet\"")
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejection.reason").value("PROFILE_MISMATCH"))
                .andExpect(jsonPath("$.rejection.comment").value("Hors sujet"))
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].details.rejectionReason").value("PROFILE_MISMATCH"))
                .andExpect(jsonPath("$[0].details.comment").value("Hors sujet"));

        patchStatus(applicationId, "SCREENING", null)
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        // La base refuse elle aussi un rejet sans motif (ck_application_rejection)
        assertThatThrownBy(() -> jdbc.sql("update application set rejection_reason = null where id = :id")
                .param("id", applicationId).update())
                .hasMessageContaining("ck_application_rejection");
    }

    /** RG-24 : l'historique est en ajout seul, même pour une requête SQL directe. */
    @Test
    void historyCannotBeModifiedNorDeleted() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);

        assertThatThrownBy(() -> jdbc.sql("update application_event set occurred_at = now() where application_id = :id")
                .param("id", applicationId).update())
                .rootCause().hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.sql("delete from application_event where application_id = :id")
                .param("id", applicationId).update())
                .rootCause().hasMessageContaining("ajout seul");
        assertThat(historyCount(applicationId)).isEqualTo(1);
    }

    private ResultActions list(String... params) throws Exception {
        var request = get("/api/v1/applications");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(recruiter.auth(request));
    }

    private void assertIds(ResultActions result, UUID... expected) throws Exception {
        String body = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(expected).map(UUID::toString).toList());
    }

    private ResultActions patchStatus(UUID applicationId, String target, String extra) throws Exception {
        return mockMvc.perform(recruiter.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"%s\",\"version\":%d%s}".formatted(target,
                        applicants.version(applicationId), extra == null ? "" : extra)));
    }

    private ResultActions move(UUID applicationId, String target, String extra) throws Exception {
        return patchStatus(applicationId, target, extra).andExpect(status().isOk());
    }

    private long historyCount(UUID applicationId) {
        return jdbc.sql("select count(*) from application_event where application_id = :id")
                .param("id", applicationId).query(Long.class).single();
    }
}
