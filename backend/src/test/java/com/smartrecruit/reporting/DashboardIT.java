package com.smartrecruit.reporting;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-44, US-45 : indicateurs exacts, limités au périmètre de l'appelant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class DashboardIT {

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
    private UUID backend;
    private UUID frontend;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
        recruiter = staff.recruiterOfNewCompany(admin);
        backend = jobs.published(recruiter, "Développeur backend", "Casablanca", null);
        frontend = jobs.published(recruiter, "Développeur frontend", "Rabat", null);
        jobs.draft(recruiter, "Brouillon", "Tanger", null);
    }

    @Test
    void recruiterSeesTheIndicatorsOfHisCompanyOnly() throws Exception {
        CandidateSession salma = applicants.readyCandidate("Salma", "Idrissi", "java");
        UUID screening = applicants.apply(salma, backend);
        UUID shortlisted = applicants.apply(applicants.readyCandidate("Karim", "Bennani", "java"), backend);
        UUID old = applicants.apply(applicants.readyCandidate("Nadia", "Alaoui", "java"), frontend);
        applicants.advance(recruiter, screening, "SCREENING");
        applicants.advance(recruiter, shortlisted, "SCREENING", "SHORTLISTED", "INTERVIEW");
        scheduleInterview(shortlisted, Instant.now().plus(2, ChronoUnit.DAYS));
        scheduleInterview(shortlisted, Instant.now().plus(9, ChronoUnit.DAYS));     // hors des 7 jours
        jdbc.sql("update application set submitted_at = now() - interval '40 days' where id = :id")
                .param("id", old).update();
        // Embauche lue dans l'historique (§6.3) : un passage à HIRED il y a 3 jours, un autre il y a 60 jours
        hireEvent(old, "3 days");
        hireEvent(screening, "60 days");
        // Une autre entreprise, très active : jamais comptée
        Session competitor = staff.recruiterOfNewCompany(admin);
        UUID otherJob = jobs.published(competitor, "Data analyst", "Fès", null);
        applicants.advance(competitor, applicants.apply(applicants.readyCandidate(), otherJob), "SCREENING");

        mockMvc.perform(recruiter.auth(get("/api/v1/company/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobsByStatus.PUBLISHED").value(2))
                .andExpect(jsonPath("$.jobsByStatus.DRAFT").value(1))
                .andExpect(jsonPath("$.applicationsReceived.last7Days").value(2))
                .andExpect(jsonPath("$.applicationsReceived.last30Days").value(2))
                .andExpect(jsonPath("$.inScreening").value(1))
                .andExpect(jsonPath("$.shortlisted").value(0))
                .andExpect(jsonPath("$.hires.last30Days").value(1))
                .andExpect(jsonPath("$.upcomingInterviews.length()").value(1))
                .andExpect(jsonPath("$.upcomingInterviews[0].candidateName").value("Karim Bennani"))
                .andExpect(jsonPath("$.upcomingInterviews[0].jobTitle").value("Développeur backend"))
                .andExpect(jsonPath("$.perJob.length()").value(3))
                .andExpect(jsonPath("$.perJob[?(@.title == 'Développeur backend')].countsByStatus.SCREENING")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.perJob[?(@.title == 'Développeur backend')].countsByStatus.INTERVIEW")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.perJob[?(@.title == 'Brouillon')].countsByStatus")
                        .value(org.hamcrest.Matchers.contains(java.util.Map.of())));
        mockMvc.perform(competitor.auth(get("/api/v1/company/dashboard")))
                .andExpect(jsonPath("$.inScreening").value(1))
                .andExpect(jsonPath("$.applicationsReceived.last30Days").value(1))
                .andExpect(jsonPath("$.hires.last30Days").value(0));
    }

    @Test
    void candidateSeesSimplifiedStatusesUpcomingInterviewsAndResume() throws Exception {
        CandidateSession salma = applicants.readyCandidate();
        UUID first = applicants.apply(salma, backend);
        applicants.apply(salma, frontend);
        applicants.advance(recruiter, first, "SCREENING", "SHORTLISTED", "INTERVIEW");
        scheduleInterview(first, Instant.now().plus(1, ChronoUnit.DAYS));

        String raw = mockMvc.perform(salma.auth(get("/api/v1/candidates/me/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationsByStatus.INTERVIEW").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.SENT").value(1))
                .andExpect(jsonPath("$.upcomingInterviews[0].jobTitle").value("Développeur backend"))
                .andExpect(jsonPath("$.upcomingInterviews[0].companyName").isNotEmpty())
                .andExpect(jsonPath("$.resume.present").value(true))
                .andExpect(jsonPath("$.profileCompleteness.percent").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("SHORTLISTED", "SCREENING", "participants", "feedback");

        CandidateSession beginner = CandidateSession.register(mockMvc);
        mockMvc.perform(beginner.auth(get("/api/v1/candidates/me/dashboard")))
                .andExpect(jsonPath("$.resume.present").value(false))
                .andExpect(jsonPath("$.applicationsByStatus").isEmpty())
                .andExpect(jsonPath("$.profileCompleteness.missing").value(org.hamcrest.Matchers.hasItem("resume")));
    }

    private void scheduleInterview(UUID applicationId, Instant startsAt) throws Exception {
        mockMvc.perform(recruiter.auth(post("/api/v1/applications/" + applicationId + "/interviews"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"%s","durationMinutes":45,"type":"PHONE","participantIds":["%s"]}"""
                                .formatted(startsAt.truncatedTo(ChronoUnit.MINUTES), recruiter.userId())))
                .andExpect(status().isCreated());
    }

    private void hireEvent(UUID applicationId, String ago) {
        jdbc.sql("""
                        insert into application_event (application_id, event_type, from_status, to_status, actor_id,
                                                       occurred_at)
                        values (:id, 'STATUS_CHANGED', 'OFFER', 'HIRED', :actor, now() - cast(:ago as interval))""")
                .param("id", applicationId).param("actor", recruiter.userId()).param("ago", ago).update();
    }
}
