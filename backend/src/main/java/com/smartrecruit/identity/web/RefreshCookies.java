package com.smartrecruit.identity.web;

import com.smartrecruit.shared.security.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie du refresh token : {@code HttpOnly} (illisible en JavaScript, donc à l'abri d'une faille XSS),
 * {@code Secure}, {@code SameSite=Strict} et limité au chemin {@code /api/v1/auth}.
 */
@Component
class RefreshCookies {

    static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private final SecurityProperties properties;

    RefreshCookies(SecurityProperties properties) {
        this.properties = properties;
    }

    String issue(String token) {
        return build(token, properties.jwt().refreshTokenTtl());
    }

    /** Même nom, même chemin et durée nulle : le navigateur supprime le cookie. */
    String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
