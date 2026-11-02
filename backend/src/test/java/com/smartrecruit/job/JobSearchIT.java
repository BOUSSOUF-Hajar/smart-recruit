package com.smartrecruit.job;

import com.smartrecruit.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-25 et US-26 : recherche et détail publics. Chaque test place ses offres dans une ville qui lui
 * est propre : la recherche voit toutes les offres de la base, y compris celles des autres tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class})
class JobSearchIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private JdbcClient jdbc;

    private Session recruiter;
    private String city;

    @BeforeEach
    void setUp() throws Exception {
        recruiter = staff.recruiterOfNewCompany(staff.admin());
        city = "Ville-" + StaffSessions.suffix();
    }

    @Test
    void searchIgnoresAccentsAndCase() throws Exception {
        UUID dev = jobs.published(recruiter, "Développeur Java", city, null);
        jobs.published(recruiter, "Comptable", city, "\"description\":\"" + "Tenue de la comptabilité générale. ".repeat(3) + "\"");

        for (String query : new String[]{"developpeur", "DÉVELOPPEUR", "Développeurs"}) {
            mockMvc.perform(get("/api/v1/jobs").param("q", query).param("city", city))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(dev.toString()));
        }
    }

    @Test
    void filtersAreCombined() throws Exception {
        UUID match = jobs.published(recruiter, "Développeur Java", city, "\"workMode\":\"REMOTE\"");
        jobs.published(recruiter, "Développeur Java", city, "\"workMode\":\"ON_SITE\"");
        jobs.published(recruiter, "Développeur Java", city,
                "\"workMode\":\"REMOTE\",\"contractType\":\"FREELANCE\"");
        jobs.published(recruiter, "Développeur Java", city,
                "\"workMode\":\"REMOTE\",\"salary\":{\"min\":5000,\"max\":8000,\"currency\":\"MAD\",\"period\":\"MONTHLY\",\"visible\":true}");

        mockMvc.perform(get("/api/v1/jobs")
                        .param("city", city.toUpperCase())
                        .param("workMode", "REMOTE,HYBRID")
                        .param("contractType", "CDI")
                        .param("experienceLevel", "MID")
                        .param("skillId", jobs.skillId("java").toString())
                        .param("salaryMin", "15000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(match.toString())));
    }

    @Test
    void draftsClosedAndExpiredOffersNeverAppear() throws Exception {
        UUID open = jobs.published(recruiter, "Ouverte", city, null);
        jobs.draft(recruiter, "Brouillon", city, null);
        UUID closed = jobs.published(recruiter, "Clôturée", city, null);
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs/" + closed + "/close"))).andExpect(status().isOk());
        UUID expired = jobs.published(recruiter, "Expirée", city, null);
        jdbc.sql("update job_offer set published_at = now() - interval '10 days', expires_at = now() - interval '1 second' where id = :id")
                .param("id", expired).update();

        mockMvc.perform(get("/api/v1/jobs").param("city", city))
                .andExpect(jsonPath("$.content[*].id", contains(open.toString())));
        mockMvc.perform(get("/api/v1/jobs/" + expired)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs/" + closed)).andExpect(status().isNotFound());
    }

    @Test
    void resultsArePagedAndTheSizeIsCapped() throws Exception {
        UUID template = jobs.published(recruiter, "Développeur", city, null);
        // 44 copies directes en base : la pagination ne dépend pas du chemin de création
        jdbc.sql("""
                        insert into job_offer (company_id, category_id, created_by, title, description, city, country_code,
                            work_mode, contract_type, experience_level, status, published_at, expires_at, created_at, updated_at)
                        select company_id, category_id, created_by, title || ' ' || n, description, city, country_code,
                            work_mode, contract_type, experience_level, status, published_at - n * interval '1 minute',
                            expires_at, now(), now()
                        from job_offer, generate_series(1, 44) as n where id = :id""")
                .param("id", template).update();

        mockMvc.perform(get("/api/v1/jobs").param("city", city))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(template.toString()));   // la plus récente d'abord
        mockMvc.perform(get("/api/v1/jobs").param("city", city).param("page", "2"))
                .andExpect(jsonPath("$.content", hasSize(5)));
        mockMvc.perform(get("/api/v1/jobs").param("city", city).param("size", "500"))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(45)));
        mockMvc.perform(get("/api/v1/jobs").param("city", city).param("sort", "title;DROP TABLE job_offer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(template.toString()));
    }

    @Test
    void hiddenSalaryIsNeverShownNorUsableAsAFilter() throws Exception {
        UUID hidden = jobs.published(recruiter, "Salaire masqué", city,
                "\"salary\":{\"min\":20000,\"max\":30000,\"currency\":\"MAD\",\"period\":\"MONTHLY\",\"visible\":false}");

        mockMvc.perform(get("/api/v1/jobs").param("city", city))
                .andExpect(jsonPath("$.content[0].salary").doesNotExist());
        mockMvc.perform(get("/api/v1/jobs/" + hidden)).andExpect(jsonPath("$.salary").doesNotExist());
        mockMvc.perform(get("/api/v1/jobs").param("city", city).param("salaryMin", "10000"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailShowsCompanyCategoryAndSkills() throws Exception {
        UUID id = jobs.published(recruiter, "Développeur Java", city, null);

        mockMvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.name").isNotEmpty())
                .andExpect(jsonPath("$.category.name").value("Développement logiciel"))
                .andExpect(jsonPath("$.requiredSkills[0].name").value("Java"))
                .andExpect(jsonPath("$.requiredSkills[0].minLevel").value("ADVANCED"))
                .andExpect(jsonPath("$.preferredSkills[*].name", containsInAnyOrder("Angular")))
                .andExpect(jsonPath("$.salary.max").value(22000))
                .andExpect(jsonPath("$.alreadyApplied").doesNotExist());

        CandidateSession candidate = CandidateSession.register(mockMvc);
        mockMvc.perform(candidate.auth(get("/api/v1/jobs/" + id)))
                .andExpect(jsonPath("$.alreadyApplied").value(false));
        mockMvc.perform(get("/api/v1/jobs").param("city", city))
                .andExpect(jsonPath("$.content[0].topSkills", contains("Java")));
    }

    @Test
    void draftDetailIsNotFound() throws Exception {
        UUID draft = jobs.draft(recruiter, "Brouillon", city, null);

        mockMvc.perform(get("/api/v1/jobs/" + draft))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
