package com.smartrecruit.company;

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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-49 : gestion des entreprises, dont la désactivation en cascade (RG-31). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class AdminCompaniesIT {

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

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
    }

    @Test
    void deactivationClosesPublishedJobsAndDisablesRecruitersInOneTransaction() throws Exception {
        UUID companyId = staff.company(admin, "Cascade " + StaffSessions.suffix());
        Session first = staff.recruiter(admin, companyId);
        Session second = staff.recruiter(admin, companyId);
        UUID published = jobs.published(first, "Développeur Java", "Casablanca", null);
        UUID other = jobs.published(first, "Développeur Angular", "Rabat", null);
        UUID draft = jobs.draft(first, "Brouillon", "Rabat", null);
        CandidateSession candidate = applicants.readyCandidate();
        UUID applicationId = applicants.apply(candidate, published);

        mockMvc.perform(admin.auth(get("/api/v1/admin/companies/{id}", companyId)))
                .andExpect(jsonPath("$.recruiterCount").value(2))
                .andExpect(jsonPath("$.jobCount").value(3))
                .andExpect(jsonPath("$.publishedJobCount").value(2));

        mockMvc.perform(admin.auth(post("/api/v1/admin/companies/{id}/deactivate", companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.active").value(false))
                .andExpect(jsonPath("$.closedJobs").value(2))
                .andExpect(jsonPath("$.disabledRecruiters").value(2));

        assertThat(jobStatus(published)).isEqualTo("CLOSED");
        assertThat(jobStatus(other)).isEqualTo("CLOSED");
        assertThat(jobStatus(draft)).isEqualTo("DRAFT");
        // Recruteurs désactivés, sessions coupées tout de suite
        mockMvc.perform(first.auth(get("/api/v1/company/dashboard"))).andExpect(status().isUnauthorized());
        mockMvc.perform(second.auth(get("/api/v1/me"))).andExpect(status().isUnauthorized());
        // Le candidat garde l'accès à son suivi
        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/{id}", applicationId)))
                .andExpect(status().isOk());
        // Audit : l'action globale avec ses chiffres, et chaque compte désactivé avec son motif
        assertThat(jdbc.sql("""
                        select details->>'closedJobs' from audit_log
                        where action = 'COMPANY_DEACTIVATED' and resource_id = :id""")
                .param("id", companyId).query(String.class).single()).isEqualTo("2");
        assertThat(jdbc.sql("""
                        select count(*) from audit_log
                        where action = 'USER_DISABLED' and details->>'reason' = 'COMPANY_DEACTIVATED'
                          and resource_id in (:a, :b)""")
                .param("a", first.userId()).param("b", second.userId()).query(Integer.class).single()).isEqualTo(2);

        // Rejouer ne change rien ; réactiver ne réactive pas les recruteurs
        mockMvc.perform(admin.auth(post("/api/v1/admin/companies/{id}/deactivate", companyId)))
                .andExpect(jsonPath("$.closedJobs").value(0));
        mockMvc.perform(admin.auth(post("/api/v1/admin/companies/{id}/activate", companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.sql("select count(*) from app_user where enabled and id in (:a, :b)")
                .param("a", first.userId()).param("b", second.userId()).query(Integer.class).single()).isZero();
    }

    /**
     * Atomicité : si la clôture d'une offre échoue (simulée par un déclencheur de test), l'entreprise
     * reste active et ses recruteurs aussi. Rien n'est appliqué à moitié.
     */
    @Test
    void aFailureInTheCascadeLeavesEverythingUnchanged() throws Exception {
        UUID companyId = staff.company(admin, "Atomique " + StaffSessions.suffix());
        Session recruiter = staff.recruiter(admin, companyId);
        UUID published = jobs.published(recruiter, "Développeur Java", "Casablanca", null);
        jdbc.sql("""
                create function fail_job_update() returns trigger language plpgsql as $$
                begin raise exception 'échec simulé'; end; $$""").update();
        jdbc.sql("""
                create trigger trg_fail_job_update before update on job_offer
                for each row when (old.company_id = '%s') execute function fail_job_update()""".formatted(companyId))
                .update();
        try {
            mockMvc.perform(admin.auth(post("/api/v1/admin/companies/{id}/deactivate", companyId)))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.sql("drop trigger trg_fail_job_update on job_offer").update();
            jdbc.sql("drop function fail_job_update()").update();
        }
        assertThat(jdbc.sql("select active from company where id = :id").param("id", companyId)
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("select enabled from app_user where id = :id").param("id", recruiter.userId())
                .query(Boolean.class).single()).isTrue();
        assertThat(jobStatus(published)).isEqualTo("PUBLISHED");
        mockMvc.perform(recruiter.auth(get("/api/v1/me"))).andExpect(status().isOk());
    }

    @Test
    void updateKeepsTheNameUniqueAndDetectsConcurrentChanges() throws Exception {
        String taken = "Déjà prise " + StaffSessions.suffix();
        staff.company(admin, taken);
        UUID companyId = staff.company(admin, "À renommer " + StaffSessions.suffix());
        long version = version(companyId);

        mockMvc.perform(admin.auth(put("/api/v1/admin/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sector":"Conseil","city":"Tanger","countryCode":"ma","version":%d}"""
                                .formatted(taken.toUpperCase(), version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String renamed = "Renommée " + StaffSessions.suffix();
        mockMvc.perform(admin.auth(put("/api/v1/admin/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sector":"Conseil","city":"Tanger","countryCode":"ma","version":%d}"""
                                .formatted(renamed, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed))
                .andExpect(jsonPath("$.countryCode").value("MA"));

        mockMvc.perform(admin.auth(put("/api/v1/admin/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Encore\",\"version\":%d}".formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(admin.auth(put("/api/v1/admin/companies/{id}", companyId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sans version\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void listIsPagedAndFilteredByNameAndState() throws Exception {
        String marker = "Pagination" + StaffSessions.suffix();
        UUID first = staff.company(admin, marker + " Alpha");
        staff.company(admin, marker + " Beta");
        UUID third = staff.company(admin, marker + " Gamma");
        mockMvc.perform(admin.auth(post("/api/v1/admin/companies/{id}/deactivate", third)))
                .andExpect(status().isOk());

        mockMvc.perform(admin.auth(get("/api/v1/admin/companies")).param("q", marker.toLowerCase())
                        .param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.toString()));
        mockMvc.perform(admin.auth(get("/api/v1/admin/companies")).param("q", marker).param("active", "false"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(third.toString()));
    }

    private String jobStatus(UUID jobId) {
        return jdbc.sql("select status from job_offer where id = :id").param("id", jobId).query(String.class).single();
    }

    private long version(UUID companyId) throws Exception {
        MvcResult result = mockMvc.perform(admin.auth(get("/api/v1/admin/companies/{id}", companyId))).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }
}
