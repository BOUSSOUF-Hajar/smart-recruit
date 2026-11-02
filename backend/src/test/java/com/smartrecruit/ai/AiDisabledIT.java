package com.smartrecruit.ai;

import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.ApplicantFixtures;
import com.smartrecruit.support.JobFixtures;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-40, RG-28 : IA désactivée → AI_DISABLED sur les endpoints IA, tout le reste fonctionne. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "smartrecruit.ai.enabled=false")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class AiDisabledIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private ApplicantFixtures applicants;

    @Test
    void aiEndpointsAnswerAiDisabledAndTheRestStillWorks() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(staff.admin());
        UUID applicationId = applicants.apply(applicants.readyCandidate(),
                jobs.published(recruiter, "Développeur Java", "Casablanca", null));

        mockMvc.perform(recruiter.auth(get("/api/v1/ai/status")))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.model").doesNotExist());
        mockMvc.perform(recruiter.auth(post("/api/v1/applications/" + applicationId + "/ai/matching")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        mockMvc.perform(recruiter.auth(patch("/api/v1/applications/" + applicationId + "/status"))
                        .contentType("application/json").content("{\"targetStatus\":\"SCREENING\",\"version\":0}"))
                .andExpect(status().isOk());
    }
}
