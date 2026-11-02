package com.smartrecruit.job;

import com.smartrecruit.TestcontainersConfiguration;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-21 à US-24 : offres côté recruteur, de bout en bout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class})
class JobOfferIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private JdbcClient jdbc;

    private Session admin;
    private Session recruiter;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
        recruiter = staff.recruiterOfNewCompany(admin);
    }

    // ---------------------------------------------------------------- US-21 création

    @Test
    void newOfferIsADraftOfMyCompanyEvenIfAnotherCompanyIsSent() throws Exception {
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Développeur backend Java", "Casablanca",
                                "\"companyId\":\"" + UUID.randomUUID() + "\",\"status\":\"PUBLISHED\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requiredSkills[0].name").value("Java"))
                .andExpect(jsonPath("$.preferredSkills[0].name").value("Angular"))
                .andExpect(jsonPath("$.createdBy.name").value("Youssef Alami"))
                .andExpect(jsonPath("$.version").value(0));

        UUID companyOfOffer = jdbc.sql("select company_id from job_offer where created_by = :user")
                .param("user", recruiter.userId()).query(UUID.class).single();
        assertThat(companyOfOffer).isEqualTo(recruiter.companyId());
    }

    @Test
    void invalidSalaryAndDuplicatedSkillAreFieldErrors() throws Exception {
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Offre", "Rabat",
                                "\"salary\":{\"min\":30000,\"max\":20000,\"currency\":\"MAD\",\"period\":\"MONTHLY\",\"visible\":true}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("salary.max"));

        String java = jobs.skillId("java").toString();
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Offre", "Rabat", "\"preferredSkills\":[{\"skillId\":\"" + java + "\"}]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("preferredSkills[0].skillId"));
    }

    @Test
    void inactiveCategoryIsRefused() throws Exception {
        UUID inactive = jdbc.sql("""
                        insert into job_category (name, active, created_at) values (:name, false, now()) returning id""")
                .param("name", "Catégorie fermée " + StaffSessions.suffix()).query(UUID.class).single();

        mockMvc.perform(recruiter.auth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Offre", "Rabat", "\"categoryId\":\"" + inactive + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    // ---------------------------------------------------------------- US-22 modification

    @Test
    void publishedOfferEditIsAuditedAndStaleVersionIsRefused() throws Exception {
        UUID id = jobs.published(recruiter, "Développeur Java", "Tanger", null);

        update(id, "Développeur Java senior", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Développeur Java senior"))
                .andExpect(jsonPath("$.version").value(2));
        update(id, "Écrasement", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        Integer audited = jdbc.sql("select count(*) from audit_log where action = 'JOB_UPDATED' and resource_id = :id")
                .param("id", id).query(Integer.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void closedOfferIsReadOnly() throws Exception {
        UUID id = jobs.published(recruiter, "Développeur Java", "Tanger", null);
        action(id, "close", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        update(id, "Modification refusée", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void offerOfAnotherCompanyIsNotFound() throws Exception {
        UUID id = jobs.draft(recruiter, "Offre confidentielle", "Fès", null);
        Session competitor = staff.recruiterOfNewCompany(admin);

        mockMvc.perform(competitor.auth(get("/api/v1/company/jobs/" + id))).andExpect(status().isNotFound());
        mockMvc.perform(competitor.auth(put("/api/v1/jobs/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Piratée", "Fès", "\"version\":0")))
                .andExpect(status().isNotFound());
        action(id, "publish", LocalDate.now().plusDays(10), competitor).andExpect(status().isNotFound());
        mockMvc.perform(competitor.auth(delete("/api/v1/jobs/" + id))).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- US-23 cycle de vie

    @Test
    void fullLifecycle() throws Exception {
        UUID id = jobs.draft(recruiter, "Développeur Java", "Agadir", null);

        action(id, "publish", LocalDate.now().plusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        action(id, "unpublish", null).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
        action(id, "publish", LocalDate.now().plusDays(30)).andExpect(status().isOk());
        action(id, "close", null).andExpect(jsonPath("$.status").value("CLOSED"));
        action(id, "reopen", LocalDate.now().minusDays(1))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.reasons[0]").value("EXPIRY_IN_PAST"));
        action(id, "reopen", LocalDate.now().plusDays(60)).andExpect(jsonPath("$.status").value("PUBLISHED"));
        action(id, "close", null).andExpect(status().isOk());
        action(id, "archive", null).andExpect(jsonPath("$.status").value("ARCHIVED"));
        action(id, "publish", LocalDate.now().plusDays(30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void publicationRulesAreExplained() throws Exception {
        UUID id = jobs.draft(recruiter, "Développeur Java", "Agadir", "\"requiredSkills\":[]");

        action(id, "publish", LocalDate.now().plusDays(200))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("JOB_NOT_PUBLISHABLE"))
                .andExpect(jsonPath("$.reasons[0]").value("EXPIRY_TOO_FAR"))
                .andExpect(jsonPath("$.reasons[1]").value("NO_REQUIRED_SKILL"));
    }

    @Test
    void onlyDraftsCanBeDeleted() throws Exception {
        UUID draft = jobs.draft(recruiter, "Brouillon", "Oujda", null);
        UUID published = jobs.published(recruiter, "Publiée", "Oujda", null);

        mockMvc.perform(recruiter.auth(delete("/api/v1/jobs/" + draft))).andExpect(status().isNoContent());
        mockMvc.perform(recruiter.auth(delete("/api/v1/jobs/" + published)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    }

    // ---------------------------------------------------------------- US-24 offres de l'entreprise

    @Test
    void companyListHidesArchivedOffersUnlessAsked() throws Exception {
        UUID draft = jobs.draft(recruiter, "Brouillon", "Meknès", null);
        UUID archived = jobs.published(recruiter, "Ancienne offre", "Meknès", null);
        action(archived, "close", null);
        action(archived, "archive", null);

        mockMvc.perform(recruiter.auth(get("/api/v1/company/jobs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(draft.toString()))
                .andExpect(jsonPath("$.content[0].applicationCounts").isMap());
        mockMvc.perform(recruiter.auth(get("/api/v1/company/jobs").param("status", "ARCHIVED")))
                .andExpect(jsonPath("$.content[*].id", hasItem(archived.toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(draft.toString()))));
    }

    private ResultActions update(UUID id, String title, long version) throws Exception {
        return mockMvc.perform(recruiter.auth(put("/api/v1/jobs/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jobs.json(title, "Tanger", "\"version\":" + version)));
    }

    private ResultActions action(UUID id, String action, LocalDate expiry) throws Exception {
        return action(id, action, expiry, recruiter);
    }

    private ResultActions action(UUID id, String action, LocalDate expiry, Session who) throws Exception {
        var request = who.auth(post("/api/v1/jobs/" + id + "/" + action));
        if (expiry != null) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"expiryDate\":\"" + expiry + "\"}");
        }
        return mockMvc.perform(request);
    }
}
