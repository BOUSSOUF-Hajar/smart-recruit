package com.smartrecruit.identity;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-48 : gestion des comptes par l'admin, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class})
class AdminUsersIT {

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
    void listFiltersByCompanyRoleStateAndTextWithoutAccents() throws Exception {
        UUID companyId = staff.company(admin, "Filtres " + StaffSessions.suffix());
        String email = createRecruiter(companyId, "Élodie", "Bérard");
        Session other = staff.recruiter(admin, companyId);

        mockMvc.perform(admin.auth(get("/api/v1/admin/users")).param("companyId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // Tri par nom : Alami avant Bérard
                .andExpect(jsonPath("$.content[0].id").value(other.userId().toString()))
                .andExpect(jsonPath("$.content[1].email").value(email))
                .andExpect(jsonPath("$.content[1].companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.content[1].jobTitle").value("Talent acquisition"))
                .andExpect(jsonPath("$.content[1].passwordHash").doesNotExist());

        mockMvc.perform(admin.auth(get("/api/v1/admin/users")).param("companyId", companyId.toString())
                        .param("q", "ELODIE berard"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value(email));

        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", other.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(admin.auth(get("/api/v1/admin/users")).param("companyId", companyId.toString())
                        .param("enabled", "false").param("role", "RECRUITER"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(other.userId().toString()));
        mockMvc.perform(admin.auth(get("/api/v1/admin/users")).param("role", "ADMIN").param("q", admin.email()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].companyId").doesNotExist());
    }

    @Test
    void disablingRevokesSessionsAtTheNextRequestAndEnablingRestoresLogin() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);
        mockMvc.perform(recruiter.auth(get("/api/v1/me"))).andExpect(status().isOk());

        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", recruiter.userId())))
                .andExpect(status().isOk());

        // Le token encore valide 15 minutes est refusé tout de suite, sans proposer de le renouveler
        mockMvc.perform(recruiter.auth(get("/api/v1/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        login(recruiter.email()).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("select count(*) from refresh_token where user_id = :id and revoked_at is null")
                .param("id", recruiter.userId()).query(Integer.class).single()).isZero();
        assertThat(audited("USER_DISABLED", recruiter.userId())).isEqualTo(1);

        // Désactiver deux fois ne réécrit pas l'audit
        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", recruiter.userId())))
                .andExpect(status().isOk());
        assertThat(audited("USER_DISABLED", recruiter.userId())).isEqualTo(1);

        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/enable", recruiter.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        login(recruiter.email()).andExpect(status().isOk());
        // L'ancien token reste refusé : seule une nouvelle connexion rend l'accès
        mockMvc.perform(recruiter.auth(get("/api/v1/me"))).andExpect(status().isUnauthorized());
        assertThat(audited("USER_ENABLED", recruiter.userId())).isEqualTo(1);
    }

    @Test
    void anAdminCannotDisableHimself() throws Exception {
        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", admin.userId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    /**
     * RG-29 sous concurrence : les deux derniers admins actifs se désactivent mutuellement au même
     * instant. Le verrou sur les admins actifs garantit qu'il en reste un.
     */
    @Test
    void theLastActiveAdminSurvivesTwoSimultaneousDeactivations() throws Exception {
        Session second = staff.admin();
        List<UUID> others = jdbc.sql("""
                        select id from app_user where role = 'ADMIN' and enabled and id not in (:a, :b)""")
                .param("a", admin.userId()).param("b", second.userId()).query(UUID.class).list();
        jdbc.sql("update app_user set enabled = false where id in (:ids)").param("ids", withSentinel(others)).update();
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Callable<Integer> first = () -> disableStatus(admin, second.userId());
            Callable<Integer> reverse = () -> disableStatus(second, admin.userId());
            List<Future<Integer>> results = pool.invokeAll(List.of(first, reverse));
            pool.shutdown();

            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsOnlyOnce(200);
            // Le perdant reçoit 409 LAST_ADMIN, ou 401 si sa propre désactivation était déjà validée
            assertThat(statuses).anyMatch(code -> code == 409 || code == 401);
            assertThat(jdbc.sql("select count(*) from app_user where enabled and id in (:a, :b)")
                    .param("a", admin.userId()).param("b", second.userId()).query(Integer.class).single())
                    .isEqualTo(1);
        } finally {
            jdbc.sql("update app_user set enabled = true where id in (:ids)").param("ids", withSentinel(others))
                    .update();
            jdbc.sql("update app_user set enabled = true where id in (:a, :b)")
                    .param("a", admin.userId()).param("b", second.userId()).update();
        }
    }

    @Test
    void updateChangesNameAndCompanyNeverTheRoleAndRevokesSessionsOnCompanyChange() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);
        UUID newCompany = staff.company(admin, "Nouvelle maison " + StaffSessions.suffix());
        long version = version(recruiter.userId());

        mockMvc.perform(admin.auth(put("/api/v1/admin/users/{id}", recruiter.userId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Yasmine","lastName":"Tazi","companyId":"%s","jobTitle":"Responsable RH",
                                 "role":"ADMIN","version":%d}""".formatted(newCompany, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Yasmine"))
                .andExpect(jsonPath("$.role").value("RECRUITER"))
                .andExpect(jsonPath("$.companyId").value(newCompany.toString()))
                .andExpect(jsonPath("$.jobTitle").value("Responsable RH"));

        // L'ancien token portait l'ancienne entreprise : il est refusé
        mockMvc.perform(recruiter.auth(get("/api/v1/company/dashboard"))).andExpect(status().isUnauthorized());
        Session again = staff.login(recruiter.email(), StaffSessions.PASSWORD);
        assertThat(again.companyId()).isEqualTo(newCompany);

        // Version obsolète : la modification concurrente n'écrase rien
        mockMvc.perform(admin.auth(put("/api/v1/admin/users/{id}", recruiter.userId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"X","lastName":"Y","companyId":"%s","version":%d}"""
                                .formatted(newCompany, version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void renamingWithoutChangingCompanyKeepsTheSession() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);
        mockMvc.perform(admin.auth(put("/api/v1/admin/users/{id}", recruiter.userId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Youssef","lastName":"El Alami","companyId":"%s","version":%d}"""
                                .formatted(recruiter.companyId(), version(recruiter.userId()))))
                .andExpect(status().isOk());
        mockMvc.perform(recruiter.auth(get("/api/v1/me"))).andExpect(status().isOk());
    }

    @Test
    void aRecruiterOfAnInactiveCompanyCannotBeEnabled() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(admin);
        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/disable", recruiter.userId())))
                .andExpect(status().isOk());
        jdbc.sql("update company set active = false where id = :id").param("id", recruiter.companyId()).update();

        mockMvc.perform(admin.auth(post("/api/v1/admin/users/{id}/enable", recruiter.userId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mockMvc.perform(admin.auth(get("/api/v1/admin/users/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    private String createRecruiter(UUID companyId, String firstName, String lastName) throws Exception {
        String email = "rh-" + StaffSessions.suffix() + "@filtres.test";
        mockMvc.perform(admin.auth(post("/api/v1/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","firstName":"%s","lastName":"%s","role":"RECRUITER","companyId":"%s",
                                 "jobTitle":"Talent acquisition"}""".formatted(email, firstName, lastName, companyId)))
                .andExpect(status().isCreated());
        return email;
    }

    private int disableStatus(Session actor, UUID target) throws Exception {
        return mockMvc.perform(actor.auth(post("/api/v1/admin/users/{id}/disable", target)))
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, StaffSessions.PASSWORD)));
    }

    private long version(UUID userId) throws Exception {
        MvcResult result = mockMvc.perform(admin.auth(get("/api/v1/admin/users/{id}", userId))).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }

    private int audited(String action, UUID resourceId) {
        return jdbc.sql("select count(*) from audit_log where action = :action and resource_id = :id")
                .param("action", action).param("id", resourceId).query(Integer.class).single();
    }

    /** {@code in ()} est invalide en SQL : une liste vide reçoit un identifiant qui ne correspond à rien. */
    private static List<UUID> withSentinel(List<UUID> ids) {
        return ids.isEmpty() ? List.of(new UUID(0, 0)) : ids;
    }
}
