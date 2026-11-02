package com.smartrecruit.company;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-47 (comptes du personnel) et US-49 (création d'entreprise), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class})
class AdminAccountsIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JdbcClient jdbc;

    private Session admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
    }

    @Test
    void recruiterGetsATemporaryPasswordShownOnceAndMustChangeIt() throws Exception {
        UUID companyId = staff.company(admin, "Atlas Digital " + StaffSessions.suffix());
        String email = "rh-" + StaffSessions.suffix() + "@atlas.test";

        MvcResult created = createStaff(email, "RECRUITER", companyId)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.user.role").value("RECRUITER"))
                .andExpect(jsonPath("$.user.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();
        String temporary = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");

        // Le mot de passe temporaire n'est stocké que haché
        String hash = jdbc.sql("select password_hash from app_user where email = :email")
                .param("email", email).query(String.class).single();
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(temporary);

        // Connexion : entreprise dans la session, changement de mot de passe exigé
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, temporary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));

        // Création auditée
        Integer audited = jdbc.sql("""
                        select count(*) from audit_log
                        where action = 'USER_CREATED' and actor_id = :admin and result = 'SUCCESS'""")
                .param("admin", admin.userId()).query(Integer.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void inactiveCompanyRefusesNewRecruitersAndNothingIsCreated() throws Exception {
        UUID companyId = staff.company(admin, "Ancienne société " + StaffSessions.suffix());
        jdbc.sql("update company set active = false where id = :id").param("id", companyId).update();
        String email = "rh-" + StaffSessions.suffix() + "@ancienne.test";

        createStaff(email, "RECRUITER", companyId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        Integer users = jdbc.sql("select count(*) from app_user where email = :email")
                .param("email", email).query(Integer.class).single();
        assertThat(users).isZero();
    }

    @Test
    void roleAndCompanyMustMatch() throws Exception {
        UUID companyId = staff.company(admin, "Société " + StaffSessions.suffix());

        createStaff("a-" + StaffSessions.suffix() + "@x.test", "RECRUITER", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));
        createStaff("b-" + StaffSessions.suffix() + "@x.test", "ADMIN", companyId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));
        createStaff("c-" + StaffSessions.suffix() + "@x.test", "CANDIDATE", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        createStaff("d-" + StaffSessions.suffix() + "@x.test", "RECRUITER", UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));
    }

    @Test
    void existingEmailIsRefused() throws Exception {
        createStaff(admin.email(), "ADMIN", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void companyNamesAreUniqueRegardlessOfCase() throws Exception {
        String name = "Maghreb Tech " + StaffSessions.suffix();
        staff.company(admin, name);

        mockMvc.perform(admin.auth(post("/api/v1/admin/companies"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        mockMvc.perform(admin.auth(get("/api/v1/admin/companies")).param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem(name)));
    }

    @Test
    void onlyAdminsManageAccounts() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);

        mockMvc.perform(recruiter.auth(post("/api/v1/admin/companies"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pirate\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions createStaff(String email, String role, UUID companyId) throws Exception {
        String company = companyId == null ? "null" : "\"" + companyId + "\"";
        return mockMvc.perform(admin.auth(post("/api/v1/admin/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","firstName":"Nadia","lastName":"Bennani","role":"%s","companyId":%s}"""
                        .formatted(email, role, company)));
    }
}
