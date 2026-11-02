package com.smartrecruit.support;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.security.Role;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comptes du personnel pour les tests d'intégration : un admin prêt à l'emploi, des entreprises et des
 * recruteurs créés par l'API d'administration, exactement comme en production. {@code @TestComponent} :
 * ignoré par le scan des composants (sinon chargé dans tous les contextes de test),
 * à importer avec {@code @Import(StaffSessions.class)}.
 */
@TestComponent
public class StaffSessions {

    public static final String PASSWORD = "phrase-de-passe-definitive-2026";

    private final MockMvc mockMvc;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;

    public StaffSessions(MockMvc mockMvc, UserRepository users, PasswordEncoder passwordEncoder, JdbcClient jdbc) {
        this.mockMvc = mockMvc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    /** Session d'un utilisateur : identifiant, token et (pour les recruteurs) entreprise. */
    public record Session(UUID userId, String email, String accessToken, UUID companyId) {

        public <T extends AbstractMockHttpServletRequestBuilder<T>> T auth(T request) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            return request;
        }
    }

    /** Admin créé directement en base, mot de passe déjà définitif. */
    public Session admin() throws Exception {
        String email = "admin-" + suffix() + "@smartrecruit.test";
        User admin = users.save(User.createStaff(email, passwordEncoder.encode(PASSWORD), "Ada", "Admin", Role.ADMIN));
        jdbc.sql("update app_user set must_change_password = false where id = :id").param("id", admin.getId()).update();
        return login(email, PASSWORD);
    }

    /** Entreprise créée par l'admin ; renvoie son identifiant. */
    public UUID company(Session admin, String name) throws Exception {
        MvcResult result = mockMvc.perform(admin.auth(post("/api/v1/admin/companies"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"city\":\"Casablanca\",\"countryCode\":\"MA\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /**
     * Recruteur créé par l'admin, qui remplace son mot de passe temporaire puis renouvelle sa session :
     * son token contient l'entreprise et plus le marqueur « mot de passe à changer ».
     */
    public Session recruiter(Session admin, UUID companyId) throws Exception {
        String email = "rh-" + suffix() + "@entreprise.test";
        MvcResult created = mockMvc.perform(admin.auth(post("/api/v1/admin/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","firstName":"Youssef","lastName":"Alami","role":"RECRUITER",
                                 "companyId":"%s","jobTitle":"Chargé de recrutement"}""".formatted(email, companyId)))
                .andExpect(status().isCreated())
                .andReturn();
        String temporary = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");

        MvcResult login = loginResult(email, temporary);
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie cookie = login.getResponse().getCookie("refresh_token");
        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(temporary, PASSWORD)))
                .andExpect(status().isNoContent());
        return login(email, PASSWORD);
    }

    /** Raccourci : nouvelle entreprise et son premier recruteur. */
    public Session recruiterOfNewCompany(Session admin) throws Exception {
        return recruiter(admin, company(admin, "Entreprise " + suffix()));
    }

    public Session login(String email, String password) throws Exception {
        String body = loginResult(email, password).getResponse().getContentAsString();
        String companyId = JsonPath.read(body, "$.user.companyId");
        return new Session(UUID.fromString(JsonPath.read(body, "$.user.id")), email,
                JsonPath.read(body, "$.accessToken"), companyId == null ? null : UUID.fromString(companyId));
    }

    private MvcResult loginResult(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    public static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
