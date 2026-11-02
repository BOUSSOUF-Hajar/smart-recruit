package com.smartrecruit.identity;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.security.JwtClaims;
import com.smartrecruit.shared.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-06 à US-11 de bout en bout : HTTP, sécurité, services et vrai PostgreSQL.
 * <p>
 * Chaque test crée ses propres comptes (emails uniques) et inscrit depuis une IP aléatoire :
 * les limiteurs de débit, partagés par tout le contexte Spring, ne se gênent pas d'un test à l'autre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

    private static final String PASSWORD = "une-phrase-de-passe-longue";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    // ---------------------------------------------------------------- US-06 inscription

    @Test
    void registrationCreatesACandidateWithAnEmptyProfileAndOpensASession() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(register(email, PASSWORD, ",\"role\":\"ADMIN\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CANDIDATE"))     // RG-02 : « role » ignoré
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=", "HttpOnly", "Secure", "SameSite=Strict",
                "Path=/api/v1/auth", "Max-Age=604800");

        UUID userId = UUID.fromString(JsonPath.read(body(result), "$.user.id"));
        Integer profiles = jdbc.sql("select count(*) from candidate_profile where user_id = :id")
                .param("id", userId).query(Integer.class).single();
        assertThat(profiles).isEqualTo(1);    // RG-04

        String hash = jdbc.sql("select password_hash from app_user where id = :id")
                .param("id", userId).query(String.class).single();
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void emailIsUniqueRegardlessOfCase() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, "")).andExpect(status().isCreated());

        mockMvc.perform(register(email.toUpperCase(), PASSWORD, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void weakOrInvalidRegistrationIsRejectedFieldByField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(randomIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pas-un-email","password":"azerty123456","firstName":"","lastName":"Idrissi",
                                 "privacyAccepted":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())   // mot de passe courant
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'privacyAccepted')]").exists());
    }

    // ---------------------------------------------------------------- US-07 connexion

    @Test
    void loginReturnsATokenAndUpdatesLastLogin() throws Exception {
        String email = registered();

        mockMvc.perform(login(email.toUpperCase(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")));

        assertThat(users.findByEmail(email)).get().extracting(User::getLastLoginAt).isNotNull();
    }

    @Test
    void wrongPasswordUnknownEmailAndDisabledAccountGetTheSameAnswer() throws Exception {
        String email = registered();
        String disabled = registered();
        User user = users.findByEmail(disabled).orElseThrow();
        user.disable();
        users.save(user);

        for (MockHttpServletRequestBuilder attempt : new MockHttpServletRequestBuilder[]{
                login(email, "mauvais-mot-de-passe"),
                login(uniqueEmail(), PASSWORD),
                login(disabled, PASSWORD)}) {
            mockMvc.perform(attempt)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect."));
        }

        // Seul le journal d'audit distingue les cas, et il survit à l'échec de la requête
        String reason = jdbc.sql("""
                        select details ->> 'reason' from audit_log
                        where action = 'LOGIN' and result = 'FAILURE' and actor_email = :email""")
                .param("email", disabled).query(String.class).single();
        assertThat(reason).isEqualTo("DISABLED");
    }

    @Test
    void sixthFailedAttemptWithinFifteenMinutesIsRateLimited() throws Exception {
        String email = registered();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(email, "mauvais-mot-de-passe")).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(email, PASSWORD))     // même le bon mot de passe est refusé
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // ---------------------------------------------------------------- US-08 rotation

    @Test
    void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
        Session first = session(registered());

        Session second = refresh(first.cookie());
        assertThat(second.cookie().getValue()).isNotEqualTo(first.cookie().getValue());
        // Le nouveau jeton fonctionne
        Session third = refresh(second.cookie());

        // L'ancien jeton rejoué : refus, et toutes les sessions du compte sont fermées
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(first.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(third.cookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void closedSessionIsRefusedWithoutClosingTheOthers() throws Exception {
        String email = registered();
        Session laptop = session(email);
        Session phone = session(email);
        mockMvc.perform(post("/api/v1/auth/logout").cookie(phone.cookie())).andExpect(status().isNoContent());

        // Jeton fermé par une déconnexion : refus simple, pas une alerte de vol
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(phone.cookie()))
                .andExpect(status().isUnauthorized());
        refresh(laptop.cookie());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    // ---------------------------------------------------------------- US-09 déconnexion

    @Test
    void logoutRevokesTheSessionClearsTheCookieAndIsIdempotent() throws Exception {
        Session session = session(registered());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session.cookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(session.cookie()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- US-10 jetons

    @Test
    void meRequiresAValidToken() throws Exception {
        Session session = session(registered());

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void expiredTokenGetsASpecificCodeSoTheFrontendCanRefresh() throws Exception {
        Instant issuedAt = Instant.now().minus(Duration.ofMinutes(30));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("smartrecruit")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .claim(JwtClaims.ROLE, Role.CANDIDATE.name())
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------------------------------------------------------------- US-11 mot de passe

    @Test
    void temporaryPasswordMustBeChangedBeforeAnythingElse() throws Exception {
        String email = staff(Role.ADMIN, "mot-de-passe-temporaire-01");
        Session session = session(email, "mot-de-passe-temporaire-01");
        assertThat(session.mustChangePassword()).isTrue();

        // Tout est bloqué, sauf /me, le changement de mot de passe et la déconnexion
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(changePassword(session, "mot-de-passe-temporaire-01", "nouvelle-phrase-secrete-2026"))
                .andExpect(status().isNoContent());

        // La session courante est gardée : un refresh donne un token sans le marqueur
        Session renewed = refresh(session.cookie());
        assertThat(renewed.mustChangePassword()).isFalse();
        mockMvc.perform(login(email, "nouvelle-phrase-secrete-2026")).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordClosesOtherSessions() throws Exception {
        String email = registered();
        Session laptop = session(email);
        Session phone = session(email);

        mockMvc.perform(changePassword(laptop, PASSWORD, "nouvelle-phrase-secrete-2026"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(phone.cookie()))
                .andExpect(status().isUnauthorized());
        refresh(laptop.cookie());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        Session session = session(registered());

        mockMvc.perform(changePassword(session, "pas-le-bon-mot-de-passe", "nouvelle-phrase-secrete-2026"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    // ---------------------------------------------------------------- outils

    private record Session(String accessToken, Cookie cookie, boolean mustChangePassword) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private String registered() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD, "")).andExpect(status().isCreated());
        return email;
    }

    private String staff(Role role, String temporaryPassword) {
        String email = uniqueEmail();
        users.save(User.createStaff(email, passwordEncoder.encode(temporaryPassword), "Test", "Staff", role));
        return email;
    }

    private Session session(String email) throws Exception {
        return session(email, PASSWORD);
    }

    private Session session(String email, String password) throws Exception {
        return toSession(mockMvc.perform(login(email, password)).andExpect(status().isOk()).andReturn());
    }

    private Session refresh(Cookie cookie) throws Exception {
        return toSession(mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age=0"))))
                .andReturn());
    }

    private Session toSession(MvcResult result) throws Exception {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        String body = body(result);
        return new Session(JsonPath.read(body, "$.accessToken"), cookie,
                JsonPath.read(body, "$.user.mustChangePassword"));
    }

    private static MockHttpServletRequestBuilder register(String email, String password, String extraJson) {
        return post("/api/v1/auth/register").with(randomIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","firstName":"Salma","lastName":"Idrissi",
                         "privacyAccepted":true%s}""".formatted(email, password, extraJson));
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private static MockHttpServletRequestBuilder changePassword(Session session, String current, String next) {
        return post("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .cookie(session.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}""".formatted(current, next));
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    /** Adresse de la plage de documentation 198.51.100.0/24, différente à chaque appel ou presque. */
    private static RequestPostProcessor randomIp() {
        String ip = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
