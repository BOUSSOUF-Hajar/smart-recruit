package com.smartrecruit.reporting;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.FakeAIService;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-46 : statistiques globales et état de l'IA (la fausse IA des tests est activée, donc disponible). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class})
class AdminDashboardIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Test
    void countsUsersByRoleAndStateCompaniesJobsApplicationsAndShowsTheAiState() throws Exception {
        Session admin = staff.admin();
        String before = dashboard(admin);

        Session recruiter = staff.recruiterOfNewCompany(admin);
        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", recruiter.userId())))
                .andExpect(status().isOk());
        String after = dashboard(admin);

        assertThat(count(after, "$.usersByRole.RECRUITER.disabled") - count(before, "$.usersByRole.RECRUITER.disabled"))
                .isEqualTo(1);
        assertThat(count(after, "$.usersByRole.RECRUITER.active")).isEqualTo(count(before, "$.usersByRole.RECRUITER.active"));
        assertThat(count(after, "$.companies.active") - count(before, "$.companies.active")).isEqualTo(1);
        assertThat(count(after, "$.usersByRole.ADMIN.active")).isPositive();

        mockMvc.perform(admin.auth(get("/api/v1/admin/dashboard")))
                .andExpect(jsonPath("$.applications.total").isNumber())
                .andExpect(jsonPath("$.jobsByStatus").isMap())
                .andExpect(jsonPath("$.ai.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.ai.model").value(FakeAIService.MODEL));
        // Le recruteur désactivé n'a même plus de session ; l'accès par rôle est couvert par SecurityMatrixIT
        mockMvc.perform(recruiter.auth(get("/api/v1/admin/dashboard"))).andExpect(status().isUnauthorized());
    }

    private String dashboard(Session admin) throws Exception {
        return mockMvc.perform(admin.auth(get("/api/v1/admin/dashboard")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long count(String json, String path) {
        Number value = JsonPath.read(json, path);
        return value.longValue();
    }
}
