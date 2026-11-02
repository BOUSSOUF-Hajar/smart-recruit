package com.smartrecruit.identity.web;

import com.smartrecruit.identity.application.AuthResult;

/** Réponse de register, login et refresh ; le refresh token part dans le cookie, pas ici. */
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {

    static AuthResponse of(AuthResult result) {
        return new AuthResponse(result.accessToken().value(), result.accessToken().expiresInSeconds(),
                UserResponse.of(result.user(), result.companyId()));
    }
}
