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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-27, US-28, US-29, US-19 : le parcours du candidat, de bout en bout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class CandidateApplicationIT {

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

    private Session recruiter;
    private UUID jobId;

    @BeforeEach
    void setUp() throws Exception {
        recruiter = staff.recruiterOfNewCompany(staff.admin());
        jobId = jobs.published(recruiter, "Développeur backend Java", "Casablanca", null);
    }

    // ---------------------------------------------------------------- US-27 postuler

    @Test
    void applicationIsSubmittedWithTheCurrentResumeFrozenAndAFirstHistoryEntry() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID resumeId = activeResume(candidate);

        String body = apply(candidate, jobId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/v1/candidates/me/applications/")))
                .andExpect(jsonPath("$.candidateStatus").value("SENT"))
                .andExpect(jsonPath("$.job.title").value("Développeur backend Java"))
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.timeline[0].candidateStatus").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        UUID applicationId = UUID.fromString(JsonPath.read(body, "$.id"));

        assertThat(jdbc.sql("select status from application where id = :id").param("id", applicationId)
                .query(String.class).single()).isEqualTo("SUBMITTED");
        assertThat(jdbc.sql("select count(*) from application_event where application_id = :id and event_type = 'CREATED'")
                .param("id", applicationId).query(Long.class).single()).isEqualTo(1);

        // RG-12 : un nouveau CV ne change pas celui de la candidature envoyée
        applicants.uploadResume(candidate, "cv-v2.pdf");
        assertThat(jdbc.sql("select resume_id from application where id = :id").param("id", applicationId)
                .query(UUID.class).single()).isEqualTo(resumeId);
    }

    @Test
    void incompleteProfileListsWhatIsMissing() throws Exception {
        CandidateSession candidate = CandidateSession.register(mockMvc);

        apply(candidate, jobId)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.missing", containsInAnyOrder("headline", "skills", "resume")));
        assertThat(applicationCount(candidate)).isZero();
    }

    @Test
    void secondApplicationIsRefusedWithALinkToTheFirst() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID first = applicants.apply(candidate, jobId);

        apply(candidate, jobId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.applicationId").value(first.toString()));
    }

    /** A4 : double clic ou double envoi. Les deux requêtes passent la vérification ; l'index tranche. */
    @Test
    void concurrentApplicationsCreateOnlyOne() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            return apply(candidate, jobId).andReturn().getResponse().getStatus();
        };
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(attempt));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
        assertThat(applicationCount(candidate)).isEqualTo(1);
    }

    @Test
    void closedOrUnknownJobsDoNotAcceptApplications() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs/" + jobId + "/close")))
                .andExpect(status().isOk());

        apply(candidate, jobId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_NOT_OPEN"));
        apply(candidate, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- US-28 suivi

    @Test
    void candidateSeesSimplifiedStatusesAndNothingInternal() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID applicationId = applicants.apply(candidate, jobId);
        move(applicationId, "SCREENING", null);
        move(applicationId, "SHORTLISTED", null);

        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateStatus").value("IN_REVIEW"))
                // SCREENING puis SHORTLISTED donnent le même libellé : une seule étape
                .andExpect(jsonPath("$.timeline[*].candidateStatus").value(
                        org.hamcrest.Matchers.contains("SENT", "IN_REVIEW")));

        move(applicationId, "REJECTED", """
                ,"rejectionReason":"INSUFFICIENT_EXPERIENCE","comment":"Commentaire strictement interne\"""");

        String detail = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andExpect(jsonPath("$.candidateStatus").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andReturn().getResponse().getContentAsString();
        String list = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].candidateStatus").value("NOT_SELECTED"))
                .andExpect(jsonPath("$.content[0].job.companyName").isNotEmpty())
                .andExpect(jsonPath("$.content[0].timeline").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // RG-14, RG-23 : ni statut interne, ni motif, ni commentaire, même dans la réponse brute
        for (String raw : List.of(detail, list)) {
            assertThat(raw).doesNotContain("REJECTED", "SHORTLISTED", "SCREENING", "INSUFFICIENT_EXPERIENCE",
                    "Commentaire strictement interne", "rejection", "version");
        }
    }

    @Test
    void applicationOfAnotherCandidateIsNotFound() throws Exception {
        UUID applicationId = applicants.apply(applicants.readyCandidate(), jobId);
        CandidateSession other = CandidateSession.register(mockMvc);

        mockMvc.perform(other.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(other.auth(post("/api/v1/candidates/me/applications/" + applicationId + "/withdraw")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- US-29 retrait

    @Test
    void withdrawalIsFinalAndForbidsApplyingAgain() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID applicationId = applicants.apply(candidate, jobId);
        String withdraw = "/api/v1/candidates/me/applications/" + applicationId + "/withdraw";

        mockMvc.perform(candidate.auth(post(withdraw)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"J'ai accepté une autre proposition\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.canWithdraw").value(false));
        mockMvc.perform(candidate.auth(post(withdraw)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        apply(candidate, jobId)
                .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"));
        // US-26 : le détail de l'offre mène à la candidature existante
        mockMvc.perform(candidate.auth(get("/api/v1/jobs/" + jobId)))
                .andExpect(jsonPath("$.alreadyApplied").value(true))
                .andExpect(jsonPath("$.myApplicationId").value(applicationId.toString()));
        // Le recruteur voit le retrait et son commentaire dans l'historique
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].toStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$[0].details.comment").value("J'ai accepté une autre proposition"));
    }

    // ---------------------------------------------------------------- US-19 suppression du CV

    @Test
    void neverSentResumeIsErased() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();

        mockMvc.perform(candidate.auth(delete("/api/v1/candidates/me/resume")))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from resume where candidate_id = :id").param("id", candidate.userId())
                .query(Long.class).single()).isZero();
        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/resume"))).andExpect(status().isNotFound());
        apply(candidate, jobId)
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.missing", containsInAnyOrder("resume")));
    }

    @Test
    void resumeSentWithAnApplicationIsOnlyDeactivatedAndStaysAvailableToTheRecruiter() throws Exception {
        CandidateSession candidate = applicants.readyCandidate();
        UUID applicationId = applicants.apply(candidate, jobId);

        mockMvc.perform(candidate.auth(delete("/api/v1/candidates/me/resume")))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from resume where candidate_id = :id and not active")
                .param("id", candidate.userId()).query(Long.class).single()).isEqualTo(1);
        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/resume"))).andExpect(status().isNotFound());
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/resume")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", endsWith("cv-salma.pdf")));
    }

    private ResultActions apply(CandidateSession candidate, UUID job) throws Exception {
        return mockMvc.perform(candidate.auth(post("/api/v1/jobs/" + job + "/applications")));
    }

    private void move(UUID applicationId, String target, String extra) throws Exception {
        mockMvc.perform(recruiter.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"%s\",\"version\":%d%s}".formatted(target,
                                applicants.version(applicationId), extra == null ? "" : extra)))
                .andExpect(status().isOk());
    }

    private UUID activeResume(CandidateSession candidate) {
        return jdbc.sql("select id from resume where candidate_id = :id and active")
                .param("id", candidate.userId()).query(UUID.class).single();
    }

    private long applicationCount(CandidateSession candidate) {
        return jdbc.sql("select count(*) from application where candidate_id = :id")
                .param("id", candidate.userId()).query(Long.class).single();
    }
}
