package com.smartrecruit.identity.application;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.security.JwtService.IssuedToken;

import java.util.UUID;

/**
 * Résultat d'une inscription, connexion ou rotation de session.
 *
 * @param refreshToken jeton en clair, à poser dans le cookie ; jamais renvoyé dans un corps JSON
 */
public record AuthResult(User user, UUID companyId, IssuedToken accessToken, String refreshToken) {
}
