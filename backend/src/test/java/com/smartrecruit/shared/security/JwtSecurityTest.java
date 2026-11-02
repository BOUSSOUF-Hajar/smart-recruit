package com.smartrecruit.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-07, US-10 : émission et validation des access tokens, sans démarrer Spring. */
class JwtSecurityTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghijklmnop";

    private final SecurityConfig config = new SecurityConfig();
    private MutableClock clock;
    private JwtService jwtService;
    private JwtDecoder decoder;
    /** Versions de session courantes, comme les tient le module Identité. */
    private final Map<UUID, Integer> currentVersions = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
        SecurityProperties properties = properties(SECRET, "smartrecruit");
        jwtService = new JwtService(config.jwtEncoder(properties), properties, clock);
        RevokedAccessTokens revocations = (userId, version) -> version < currentVersions.getOrDefault(userId, 0);
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("revocations", revocations));
        decoder = config.jwtDecoder(properties, clock, beans.getBeanProvider(RevokedAccessTokens.class));
    }

    @Test
    void aTokenOfAnOlderSessionVersionIsRefusedWithoutBeingCalledExpired() {
        UUID userId = UUID.randomUUID();
        String before = jwtService.issue(userId, Role.RECRUITER, UUID.randomUUID(), false, 0).value();
        String after = jwtService.issue(userId, Role.RECRUITER, UUID.randomUUID(), false, 1).value();
        currentVersions.put(userId, 1);

        assertThatThrownBy(() -> decoder.decode(before))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(error -> assertThat(ApiAuthenticationEntryPoint.isExpiredToken(error)).isFalse());
        assertThat(decoder.decode(after).getClaims()).containsEntry(JwtClaims.SESSION_VERSION, 1L);
        // Les autres comptes ne sont pas concernés
        assertThat(decoder.decode(jwtService.issue(UUID.randomUUID(), Role.ADMIN, null, false).value())).isNotNull();
    }

    @Test
    void tokenCarriesUserRoleAndCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        JwtService.IssuedToken token = jwtService.issue(userId, Role.RECRUITER, companyId, false);
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString(JwtClaims.ROLE)).isEqualTo("RECRUITER");
        assertThat(jwt.getClaimAsString(JwtClaims.COMPANY_ID)).isEqualTo(companyId.toString());
        assertThat(jwt.hasClaim(JwtClaims.PASSWORD_CHANGE_REQUIRED)).isFalse();
        assertThat(jwt.getHeaders()).containsEntry("alg", MacAlgorithm.HS256.getName());
    }

    @Test
    void temporaryPasswordIsFlaggedInTheToken() {
        Jwt jwt = decoder.decode(jwtService.issue(UUID.randomUUID(), Role.ADMIN, null, true).value());

        assertThat(jwt.getClaimAsBoolean(JwtClaims.PASSWORD_CHANGE_REQUIRED)).isTrue();
        assertThat(jwt.hasClaim(JwtClaims.COMPANY_ID)).isFalse();
    }

    @Test
    void expiredTokenIsRejectedAndRecognisedAsExpired() {
        String token = jwtService.issue(UUID.randomUUID(), Role.CANDIDATE, null, false).value();
        // 15 minutes de validité + 60 secondes de tolérance d'horloge
        clock.advance(Duration.ofMinutes(17));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(e -> assertThat(ApiAuthenticationEntryPoint.isExpiredToken(e)).isTrue());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        SecurityProperties other = properties("another-secret-0123456789abcdefghijklmnopq", "smartrecruit");
        String forged = new JwtService(config.jwtEncoder(other), other, clock)
                .issue(UUID.randomUUID(), Role.ADMIN, null, false).value();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(BadJwtException.class)
                .satisfies(e -> assertThat(ApiAuthenticationEntryPoint.isExpiredToken(e)).isFalse());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        SecurityProperties other = properties(SECRET, "someone-else");
        String token = new JwtService(config.jwtEncoder(other), other, clock)
                .issue(UUID.randomUUID(), Role.ADMIN, null, false).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        // En-tête {"alg":"none"} : l'attaque classique contre les bibliothèques JWT permissives
        String unsigned = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4Iiwicm9sZSI6IkFETUlOIn0.";

        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void roleClaimBecomesASpringRole() {
        Jwt jwt = decoder.decode(jwtService.issue(UUID.randomUUID(), Role.CANDIDATE, null, false).value());

        assertThat(SecurityConfig.roleConverter().convert(jwt).getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_CANDIDATE");
    }

    @Test
    void currentUserIsRebuiltFromTheToken() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Jwt jwt = decoder.decode(jwtService.issue(userId, Role.RECRUITER, companyId, false).value());

        CurrentUser user = CurrentUserArgumentResolver.fromJwt(jwt);

        assertThat(user).isEqualTo(new CurrentUser(userId, Role.RECRUITER, companyId));
        assertThat(user.isRecruiterOf(companyId)).isTrue();
        assertThat(user.isRecruiterOf(UUID.randomUUID())).isFalse();
    }

    private static SecurityProperties properties(String secret, String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7)),
                new SecurityProperties.RefreshCookie(true),
                new SecurityProperties.RateLimit(5, Duration.ofMinutes(15), 10, Duration.ofHours(1)));
    }
}
