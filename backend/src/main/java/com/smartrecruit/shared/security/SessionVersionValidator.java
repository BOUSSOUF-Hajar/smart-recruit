package com.smartrecruit.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Refuse un access token dont la version de session est dépassée (compte désactivé, entreprise changée).
 * Le refus donne {@code 401 AUTHENTICATION_REQUIRED} (pas « expiré ») : le frontend ne tente pas de
 * renouveler la session, il déconnecte.
 */
class SessionVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "Session révoquée", null);

    private final ObjectProvider<RevokedAccessTokens> revocations;

    SessionVersionValidator(ObjectProvider<RevokedAccessTokens> revocations) {
        this.revocations = revocations;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        RevokedAccessTokens registry = revocations.getIfAvailable();
        String subject = jwt.getSubject();
        if (registry == null || subject == null) {
            return OAuth2TokenValidatorResult.success();
        }
        Number version = jwt.getClaim(JwtClaims.SESSION_VERSION);
        boolean revoked = registry.isRevoked(UUID.fromString(subject), version == null ? 0 : version.intValue());
        return revoked ? OAuth2TokenValidatorResult.failure(REVOKED) : OAuth2TokenValidatorResult.success();
    }
}
