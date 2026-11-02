package com.smartrecruit.shared.security;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * US-10 : chaque ligne de la matrice de {@code docs/api.md} §11, pour chaque rôle.
 * <p>
 * Seule la décision d'accès est vérifiée : beaucoup d'endpoints n'existent pas encore.
 * « Refusé » signifie {@code 401 AUTHENTICATION_REQUIRED} sans token et {@code 403 ACCESS_DENIED}
 * avec un rôle insuffisant ; « autorisé » signifie que la requête a passé la sécurité, quelle que soit
 * ensuite la réponse du contrôleur (404 pour un endpoint à venir, 400 pour un corps vide,
 * 401 AUTH_REFRESH_INVALID pour un refresh sans cookie…).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityMatrixIT {

    private static final String ID = "0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Colonne « accès » : {@code PUBLIC}, {@code AUTHENTICATED}, {@code NONE} ou une liste de rôles
     * séparés par {@code |}.
     */
    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource(delimiter = ';', textBlock = """
            GET    ; /actuator/health                       ; PUBLIC
            GET    ; /actuator/health/readiness             ; PUBLIC
            GET    ; /actuator/info                         ; ADMIN
            POST   ; /api/v1/auth/register                  ; PUBLIC
            POST   ; /api/v1/auth/login                     ; PUBLIC
            POST   ; /api/v1/auth/refresh                   ; PUBLIC
            POST   ; /api/v1/auth/logout                    ; PUBLIC
            GET    ; /api/v1/jobs                           ; PUBLIC
            GET    ; /api/v1/jobs/{id}                      ; PUBLIC
            GET    ; /api/v1/categories                     ; PUBLIC
            POST   ; /api/v1/jobs/{id}/applications         ; CANDIDATE
            POST   ; /api/v1/jobs                           ; RECRUITER
            PUT    ; /api/v1/jobs/{id}                      ; RECRUITER
            DELETE ; /api/v1/jobs/{id}                      ; RECRUITER
            POST   ; /api/v1/jobs/{id}/publish              ; RECRUITER
            POST   ; /api/v1/jobs/{id}/close                ; RECRUITER
            GET    ; /api/v1/jobs/{id}/applications         ; NONE
            GET    ; /api/v1/me                             ; AUTHENTICATED
            POST   ; /api/v1/auth/password                  ; AUTHENTICATED
            GET    ; /api/v1/skills                         ; AUTHENTICATED
            POST   ; /api/v1/skills                         ; CANDIDATE|RECRUITER
            GET    ; /api/v1/candidates/me                  ; CANDIDATE
            PUT    ; /api/v1/candidates/me/profile          ; CANDIDATE
            DELETE ; /api/v1/candidates/me/resume           ; CANDIDATE
            GET    ; /api/v1/candidates/me/applications     ; CANDIDATE
            GET    ; /api/v1/candidates/me/interviews       ; CANDIDATE
            GET    ; /api/v1/candidates/me/dashboard        ; CANDIDATE
            POST   ; /api/v1/candidates/me/applications/{id}/withdraw ; CANDIDATE
            GET    ; /api/v1/company/dashboard              ; RECRUITER
            GET    ; /api/v1/company/jobs                   ; RECRUITER
            GET    ; /api/v1/company/jobs/{id}              ; RECRUITER
            GET    ; /api/v1/applications                   ; RECRUITER
            GET    ; /api/v1/applications/{id}              ; RECRUITER
            PATCH  ; /api/v1/applications/{id}/status       ; RECRUITER
            GET    ; /api/v1/applications/{id}/history      ; RECRUITER
            GET    ; /api/v1/applications/{id}/resume       ; RECRUITER
            GET    ; /api/v1/applications/{id}/notes        ; RECRUITER
            POST   ; /api/v1/applications/{id}/notes        ; RECRUITER
            POST   ; /api/v1/applications/{id}/interviews   ; RECRUITER
            POST   ; /api/v1/interviews/{id}/complete       ; RECRUITER
            GET    ; /api/v1/company/recruiters             ; RECRUITER
            GET    ; /api/v1/interviews                     ; RECRUITER
            GET    ; /api/v1/interviews/{id}                ; RECRUITER
            POST   ; /api/v1/ai/tasks                       ; RECRUITER
            GET    ; /api/v1/ai/status                      ; RECRUITER
            GET    ; /api/v1/ai/tasks/{id}                  ; RECRUITER
            POST   ; /api/v1/applications/{id}/ai/matching  ; RECRUITER
            GET    ; /api/v1/applications/{id}/ai/resume-analysis ; RECRUITER
            GET    ; /api/v1/admin/users                    ; ADMIN
            POST   ; /api/v1/admin/users                    ; ADMIN
            GET    ; /api/v1/admin/users/{id}               ; ADMIN
            PUT    ; /api/v1/admin/users/{id}               ; ADMIN
            POST   ; /api/v1/admin/users/{id}/disable       ; ADMIN
            POST   ; /api/v1/admin/users/{id}/enable        ; ADMIN
            GET    ; /api/v1/admin/companies                ; ADMIN
            POST   ; /api/v1/admin/companies                ; ADMIN
            PUT    ; /api/v1/admin/companies/{id}           ; ADMIN
            POST   ; /api/v1/admin/companies/{id}/deactivate ; ADMIN
            POST   ; /api/v1/admin/companies/{id}/activate  ; ADMIN
            GET    ; /api/v1/admin/categories               ; ADMIN
            POST   ; /api/v1/admin/categories               ; ADMIN
            DELETE ; /api/v1/admin/categories/{id}          ; ADMIN
            GET    ; /api/v1/admin/skills                   ; ADMIN
            POST   ; /api/v1/admin/skills/{id}/merge        ; ADMIN
            GET    ; /api/v1/admin/audit-logs               ; ADMIN
            GET    ; /api/v1/admin/audit-logs/actions       ; ADMIN
            GET    ; /api/v1/admin/dashboard                ; ADMIN
            GET    ; /api/v1/unknown                        ; NONE
            GET    ; /internal/anything                     ; NONE
            """)
    void accessMatchesTheMatrix(String method, String path, String access) throws Exception {
        String uri = path.replace("{id}", ID);
        Set<String> allowedRoles = Arrays.stream(access.split("\\|")).collect(Collectors.toSet());

        Outcome anonymous = call(method, uri, null);
        if (access.equals("PUBLIC")) {
            assertThat(anonymous.passedSecurity()).as("anonyme : %s", anonymous).isTrue();
        } else {
            assertThat(anonymous).as("anonyme").isEqualTo(new Outcome(401, "AUTHENTICATION_REQUIRED"));
        }

        for (Role role : Role.values()) {
            boolean allowed = access.equals("PUBLIC") || access.equals("AUTHENTICATED")
                    || allowedRoles.contains(role.name());
            Outcome outcome = call(method, uri, role);
            if (allowed) {
                assertThat(outcome.passedSecurity()).as("%s : %s", role, outcome).isTrue();
            } else {
                assertThat(outcome).as(role.name()).isEqualTo(new Outcome(403, "ACCESS_DENIED"));
            }
        }
    }

    private record Outcome(int status, String code) {
        boolean passedSecurity() {
            return !"AUTHENTICATION_REQUIRED".equals(code) && !"ACCESS_DENIED".equals(code);
        }
    }

    private Outcome call(String method, String uri, Role role) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (role != null) {
            // Token déjà validé, rôle fourni directement : on teste les règles, pas le décodage
            request.with(jwt()
                    .jwt(token -> token.subject(UUID.randomUUID().toString()).claim(JwtClaims.ROLE, role.name()))
                    .authorities(new SimpleGrantedAuthority(role.authority())));
        }
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString();
        String code = body.contains("\"code\"") ? JsonPath.read(body, "$.code") : null;
        return new Outcome(response.getStatus(), code);
    }
}
