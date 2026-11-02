package com.smartrecruit.shared.domain;

import java.util.Map;

/**
 * Échec d'authentification : identifiants refusés ou session invalide.
 * Le message est volontairement générique : il ne dit jamais lequel de l'email
 * ou du mot de passe est faux, ni si le compte est désactivé (F-AUTH-02 A1, A2).
 */
public class AuthenticationFailedException extends DomainException {

    private AuthenticationFailedException(ErrorCode code, String message) {
        super(code, message, Map.of());
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Email ou mot de passe incorrect.");
    }

    public static AuthenticationFailedException invalidRefreshToken() {
        return new AuthenticationFailedException(ErrorCode.AUTH_REFRESH_INVALID,
                "Votre session a expiré. Reconnectez-vous.");
    }
}
