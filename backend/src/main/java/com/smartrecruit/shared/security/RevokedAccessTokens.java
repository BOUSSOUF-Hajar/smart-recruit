package com.smartrecruit.shared.security;

import java.util.UUID;

/**
 * Révocation des access tokens avant leur expiration (US-48) : un compte désactivé, ou un
 * recruteur changé d'entreprise, ne doit pas garder ses droits pendant les 15 minutes restantes.
 * <p>
 * Implémentée par le module Identité, qui tient la version de session de chaque compte.
 * Le contrôle se fait en mémoire, sans requête à chaque appel.
 */
public interface RevokedAccessTokens {

    /** @param sessionVersion version portée par le token (claim {@link JwtClaims#SESSION_VERSION}, 0 si absente) */
    boolean isRevoked(UUID userId, int sessionVersion);
}
