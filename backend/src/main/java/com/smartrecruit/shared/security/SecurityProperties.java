package com.smartrecruit.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Réglages de sécurité, validés au démarrage : une clé trop courte empêche l'application de démarrer.
 */
@Validated
@ConfigurationProperties(prefix = "smartrecruit.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull RefreshCookie refreshCookie,
                                 @Valid @NotNull RateLimit rateLimit) {

    /**
     * @param secret clé HMAC-SHA256 ; 32 caractères minimum, soit 256 bits (ENF-SEC-04)
     */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "doit faire au moins 32 caractères (256 bits)") String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    /** @param secure attribut Secure du cookie ; à garder vrai, y compris en développement */
    public record RefreshCookie(boolean secure) {
    }

    public record RateLimit(
            @Min(1) int loginFailures,
            @NotNull Duration loginWindow,
            @Min(1) int registrationsPerIp,
            @NotNull Duration registrationWindow) {
    }
}
