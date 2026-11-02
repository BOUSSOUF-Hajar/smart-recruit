package com.smartrecruit.shared.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Émission des access tokens (JWT signés HMAC-SHA256, 15 minutes).
 * La validation est faite par Spring Security ({@code JwtDecoder}), jamais à la main.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties.Jwt properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties.jwt();
        this.clock = clock;
    }

    public IssuedToken issue(UUID userId, Role role, UUID companyId, boolean passwordChangeRequired) {
        return issue(userId, role, companyId, passwordChangeRequired, 0);
    }

    /** @param sessionVersion version de session du compte ; un token d'une version antérieure est refusé */
    public IssuedToken issue(UUID userId, Role role, UUID companyId, boolean passwordChangeRequired,
                             int sessionVersion) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(JwtClaims.ROLE, role.name());
        if (companyId != null) {
            claims.claim(JwtClaims.COMPANY_ID, companyId.toString());
        }
        if (passwordChangeRequired) {
            claims.claim(JwtClaims.PASSWORD_CHANGE_REQUIRED, true);
        }
        if (sessionVersion > 0) {
            claims.claim(JwtClaims.SESSION_VERSION, sessionVersion);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, properties.accessTokenTtl().toSeconds());
    }

    /** @param expiresInSeconds durée de validité, renvoyée au client dans {@code expiresIn} */
    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
