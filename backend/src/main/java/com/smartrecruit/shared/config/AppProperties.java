package com.smartrecruit.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Propriétés générales de l'application, validées au démarrage.
 *
 * @param publicUrl       URL publique du frontend (origine CORS en dev, liens absolus plus tard)
 * @param defaultTimezone fuseau par défaut des entreprises (calcul de l'expiration des offres)
 */
@Validated
@ConfigurationProperties(prefix = "smartrecruit.app")
public record AppProperties(
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "doit commencer par http:// ou https://")
        String publicUrl,

        @NotNull
        ZoneId defaultTimezone
) {
}
