package com.smartrecruit.audit;

import com.smartrecruit.TestcontainersConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-53 : consultation filtrée du journal d'audit, en lecture seule. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class})
class AuditLogConsultationIT {

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
    void filtersByActorActionResultAndPeriodNewestFirst() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.SECONDS);
        String firstName = "Audit A " + StaffSessions.suffix();
        String secondName = "Audit B " + StaffSessions.suffix();
        createCategory(firstName);
        createCategory(secondName);
        String unknown = "inconnu-" + StaffSessions.suffix() + "@nulle-part.test";
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"mauvais-mot-de-passe\"}".formatted(unknown)))
                .andExpect(status().isUnauthorized());

        // Par auteur et action : les deux créations, la plus récente d'abord, avec le nom de l'auteur
        mockMvc.perform(admin.auth(get("/api/v1/admin/audit-logs"))
                        .param("actorId", admin.userId().toString()).param("action", "CATEGORY_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].details.name").value(secondName))
                .andExpect(jsonPath("$.content[1].details.name").value(firstName))
                .andExpect(jsonPath("$.content[0].actorName").value("Ada Admin"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(admin.email()))
                .andExpect(jsonPath("$.content[0].result").value("SUCCESS"));

        // Par résultat et période : l'échec de connexion, avec l'email tenté et l'adresse IP
        mockMvc.perform(admin.auth(get("/api/v1/admin/audit-logs"))
                        .param("result", "FAILURE").param("action", "LOGIN")
                        .param("from", start.toString()).param("to", Instant.now().plusSeconds(5).toString()))
                .andExpect(jsonPath("$.content[*].actorEmail", hasItem(unknown)))
                .andExpect(jsonPath("$.content[*].result", everyItem(is("FAILURE"))))
                .andExpect(jsonPath("$.content[0].ipAddress").isNotEmpty());

        // Une période passée n'en contient aucune
        mockMvc.perform(admin.auth(get("/api/v1/admin/audit-logs"))
                        .param("actorId", admin.userId().toString())
                        .param("to", start.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(admin.auth(get("/api/v1/admin/audit-logs"))
                        .param("from", start.toString()).param("to", start.toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(admin.auth(get("/api/v1/admin/audit-logs/actions")))
                .andExpect(jsonPath("$", hasItem("CATEGORY_CREATED")))
                .andExpect(jsonPath("$", hasItem("LOGIN")));
    }

    @Test
    void theJournalCanNeitherBeModifiedNorDeleted() throws Exception {
        createCategory("Immuable " + StaffSessions.suffix());

        mockMvc.perform(admin.auth(delete("/api/v1/admin/audit-logs"))).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(admin.auth(put("/api/v1/admin/audit-logs"))).andExpect(status().isMethodNotAllowed());
        assertThatThrownBy(() -> jdbc.sql("update audit_log set action = 'FALSIFIE' where actor_id = :id")
                .param("id", admin.userId()).update())
                .rootCause().hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_log where actor_id = :id")
                .param("id", admin.userId()).update())
                .rootCause().hasMessageContaining("ajout seul");
        assertThat(jdbc.sql("select count(*) from audit_log where actor_id = :id").param("id", admin.userId())
                .query(Integer.class).single()).isPositive();
    }

    @Test
    void onlyAnAdminReadsTheJournal() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);
        mockMvc.perform(recruiter.auth(get("/api/v1/admin/audit-logs"))).andExpect(status().isForbidden());
    }

    private void createCategory(String name) throws Exception {
        mockMvc.perform(admin.auth(post("/api/v1/admin/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated());
    }
}
