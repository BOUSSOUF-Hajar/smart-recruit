package com.smartrecruit.interview;

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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-35 à US-38, RG-21, RG-22, RG-33 : les entretiens de bout en bout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class InterviewIT {

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
    private CandidateSession candidate;
    private UUID applicationId;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
        recruiter = staff.recruiterOfNewCompany(admin);
        UUID jobId = jobs.published(recruiter, "Développeur backend Java", "Casablanca", null);
        candidate = applicants.readyCandidate();
        applicationId = applicants.apply(candidate, jobId);
    }

    // ---------------------------------------------------------------- US-35 planifier

    @Test
    void interviewNeedsAnApplicationAtTheInterviewStage() throws Exception {
        schedule(video(inDays(3))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_ALLOWED"));
    }

    /** RG-32 : 10 h à UTC+1 est stocké en UTC (9 h) et renvoyé comme tel ; le frontend le réaffiche à 10 h. */
    @Test
    void interviewIsStoredInUtcAndAppearsInTheHistory() throws Exception {
        toInterviewStage();
        OffsetDateTime tenAm = OffsetDateTime.now(ZoneOffset.ofHours(1)).plusDays(3)
                .withHour(10).truncatedTo(ChronoUnit.HOURS);

        String body = schedule(video(tenAm.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.application.candidateName").value("Salma Idrissi"))
                .andExpect(jsonPath("$.participants[0].name").value("Youssef Alami"))
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(body, "$.startsAt"))).isEqualTo(tenAm.toInstant());
        assertThat((String) JsonPath.read(body, "$.startsAt")).endsWith("Z");
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].type").value("INTERVIEW_SCHEDULED"))
                .andExpect(jsonPath("$[0].actor.name").value("Youssef Alami"));
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId)))
                .andExpect(jsonPath("$.interviews[0].type").value("VIDEO"));
    }

    @Test
    void locationAndParticipantsAreValidated() throws Exception {
        toInterviewStage();
        Session outsider = staff.recruiterOfNewCompany(admin);

        schedule(body(inDays(3), "VIDEO", null, null, recruiter.userId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("meetingUrl"));
        schedule(body(inDays(3), "ON_SITE", null, null, recruiter.userId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("address"));
        schedule(body(inDays(3), "PHONE", null, null, outsider.userId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("participantIds"));
        schedule(body(Instant.now().minus(1, ChronoUnit.HOURS).toString(), "PHONE", null, null, recruiter.userId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("startsAt"));
        assertThat(interviewCount()).isZero();
    }

    // ---------------------------------------------------------------- US-36 reprogrammer, annuler

    @Test
    void rescheduleKeepsBothDatesAndFinishedInterviewsAreReadOnly() throws Exception {
        toInterviewStage();
        String created = schedule(video(inDays(3))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        String first = JsonPath.read(created, "$.startsAt");
        String later = inDays(5);

        mockMvc.perform(recruiter.auth(put("/api/v1/interviews/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(video(later), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].type").value("INTERVIEW_RESCHEDULED"))
                .andExpect(jsonPath("$[0].details.previousStartsAt").value(first))
                .andExpect(jsonPath("$[0].details.startsAt").value(Instant.parse(later).toString()));
        mockMvc.perform(recruiter.auth(put("/api/v1/interviews/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(video(later), 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        mockMvc.perform(recruiter.auth(post("/api/v1/interviews/" + id + "/cancel"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Candidat indisponible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Candidat indisponible"));
        mockMvc.perform(recruiter.auth(put("/api/v1/interviews/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(video(later), 2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // ---------------------------------------------------------------- US-37 clôturer, RG-33

    /** Critère de fin de la phase 7 : une candidature va de SUBMITTED à HIRED. */
    @Test
    void completedInterviewUnlocksTheOfferAndTheHiring() throws Exception {
        toInterviewStage();
        String id = JsonPath.read(schedule(video(inDays(1))).andReturn().getResponse().getContentAsString(), "$.id");
        Session colleague = staff.recruiter(admin, recruiter.companyId());

        complete(recruiter, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        moveStatus("OFFER").andExpect(jsonPath("$.code").value("OFFER_REQUIRES_COMPLETED_INTERVIEW"));

        // Le temps passe : l'entretien a commencé
        jdbc.sql("update interview set starts_at = now() - interval '1 hour' where id = :id")
                .param("id", UUID.fromString(id)).update();
        complete(colleague, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        complete(recruiter, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.feedback.rating").value("POSITIVE"))
                .andExpect(jsonPath("$.feedback.comment").value("Excellente maîtrise de Spring"));

        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId)))
                .andExpect(jsonPath("$.allowedTransitions").value(contains("OFFER", "REJECTED")));
        moveStatus("OFFER").andExpect(status().isOk());
        moveStatus("HIRED").andExpect(status().isOk()).andExpect(jsonPath("$.allowedTransitions").isEmpty());
        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andExpect(jsonPath("$.candidateStatus").value("HIRED"));
    }

    // ---------------------------------------------------------------- RG-22

    @Test
    void rejectingTheApplicationCancelsItsScheduledInterviews() throws Exception {
        toInterviewStage();
        schedule(video(inDays(2))).andExpect(status().isCreated());
        schedule(video(inDays(4))).andExpect(status().isCreated());

        mockMvc.perform(recruiter.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REJECTED\",\"rejectionReason\":\"POSITION_FILLED\",\"version\":%d}"
                                .formatted(applicants.version(applicationId))))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("select status from interview where application_id = :id").param("id", applicationId)
                .query(String.class).list()).containsOnly("CANCELLED");
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].type").value("INTERVIEW_CANCELLED"))
                .andExpect(jsonPath("$[0].details.automatic").value(true))
                .andExpect(jsonPath("$[2].toStatus").value("REJECTED"));
    }

    @Test
    void withdrawalCancelsScheduledInterviewsToo() throws Exception {
        toInterviewStage();
        schedule(video(inDays(2))).andExpect(status().isCreated());

        mockMvc.perform(candidate.auth(post("/api/v1/candidates/me/applications/" + applicationId + "/withdraw")))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("select status from interview where application_id = :id").param("id", applicationId)
                .query(String.class).single()).isEqualTo("CANCELLED");
    }

    // ---------------------------------------------------------------- US-38, RG-23

    @Test
    void candidateSeesUpcomingInterviewsWithoutFeedbackNorParticipants() throws Exception {
        toInterviewStage();
        String done = JsonPath.read(schedule(video(inDays(1))).andReturn().getResponse().getContentAsString(), "$.id");
        String cancelled = JsonPath.read(schedule(video(inDays(3))).andReturn().getResponse().getContentAsString(), "$.id");
        schedule(video(inDays(2))).andExpect(status().isCreated());
        mockMvc.perform(recruiter.auth(post("/api/v1/interviews/" + cancelled + "/cancel"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motif interne confidentiel\"}"))
                .andExpect(status().isOk());
        jdbc.sql("update interview set starts_at = now() - interval '1 day' where id = :id")
                .param("id", UUID.fromString(done)).update();
        complete(recruiter, done).andExpect(status().isOk());

        String upcoming = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/interviews")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status").value(contains("SCHEDULED", "CANCELLED")))
                .andExpect(jsonPath("$[0].jobTitle").value("Développeur backend Java"))
                .andExpect(jsonPath("$[0].meetingUrl").value("https://meet.example.com/abc-defg"))
                .andExpect(jsonPath("$[0].candidateInstructions").value("Prévoir 10 minutes de présentation"))
                .andReturn().getResponse().getContentAsString();
        String past = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/interviews").param("upcoming", "false")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String detail = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andExpect(jsonPath("$.interviews.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        for (String raw : List.of(upcoming, past, detail)) {
            assertThat(raw).doesNotContain("feedback", "POSITIVE", "Excellente maîtrise", "participants",
                    "Youssef", "Motif interne confidentiel", "cancellationReason");
        }
    }

    @Test
    void agendaIsLimitedToMyCompanyAndCanShowOnlyMine() throws Exception {
        toInterviewStage();
        Session colleague = staff.recruiter(admin, recruiter.companyId());
        String id = JsonPath.read(schedule(video(inDays(2))).andReturn().getResponse().getContentAsString(), "$.id");
        schedule(body(inDays(3), "PHONE", null, null, colleague.userId())).andExpect(status().isCreated());
        Session outsider = staff.recruiterOfNewCompany(admin);

        mockMvc.perform(recruiter.auth(get("/api/v1/interviews")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(recruiter.auth(get("/api/v1/interviews").param("mine", "true")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(recruiter.auth(get("/api/v1/interviews").param("applicationId", applicationId.toString())
                        .param("status", "SCHEDULED")))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(outsider.auth(get("/api/v1/interviews"))).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(outsider.auth(get("/api/v1/interviews/" + id))).andExpect(status().isNotFound());
        mockMvc.perform(outsider.auth(post("/api/v1/interviews/" + id + "/cancel"))).andExpect(status().isNotFound());
    }

    private void toInterviewStage() throws Exception {
        applicants.advance(recruiter, applicationId, "SCREENING", "SHORTLISTED", "INTERVIEW");
    }

    private ResultActions schedule(String json) throws Exception {
        return mockMvc.perform(recruiter.auth(post("/api/v1/applications/" + applicationId + "/interviews"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions complete(Session who, String id) throws Exception {
        return mockMvc.perform(who.auth(post("/api/v1/interviews/" + id + "/complete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"POSITIVE\",\"comment\":\"Excellente maîtrise de Spring\"}"));
    }

    private ResultActions moveStatus(String target) throws Exception {
        return mockMvc.perform(recruiter.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"%s\",\"version\":%d}".formatted(target, applicants.version(applicationId))));
    }

    private String video(String startsAt) {
        return body(startsAt, "VIDEO", "https://meet.example.com/abc-defg", null, recruiter.userId());
    }

    private static String body(String startsAt, String type, String url, String address, UUID participant) {
        return """
                {"startsAt":"%s","durationMinutes":45,"type":"%s","meetingUrl":%s,"address":%s,
                 "candidateInstructions":"Prévoir 10 minutes de présentation","participantIds":["%s"]}"""
                .formatted(startsAt, type, url == null ? "null" : "\"" + url + "\"",
                        address == null ? "null" : "\"" + address + "\"", participant);
    }

    private static String withVersion(String json, long version) {
        return json.substring(0, json.lastIndexOf('}')) + ",\"version\":" + version + "}";
    }

    private static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString();
    }

    private long interviewCount() {
        return jdbc.sql("select count(*) from interview where application_id = :id").param("id", applicationId)
                .query(Long.class).single();
    }
}
