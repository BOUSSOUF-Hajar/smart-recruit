package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCatalogTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAnErrorStatusATitleAndAType(ErrorCode code) {
        assertThat(ErrorCatalog.status(code).value()).isBetween(400, 599);
        assertThat(ErrorCatalog.title(code)).isNotBlank();
        assertThat(ErrorCatalog.type(code)).matches("https://smartrecruit\\.dev/errors/[a-z-]+");
    }

    /** Extrait du catalogue de l'analyse fonctionnelle (§7). */
    @ParameterizedTest
    @CsvSource({
            "VALIDATION_FAILED, 400",
            "AUTH_TOKEN_EXPIRED, 401",
            "ACCESS_DENIED, 403",
            "RESOURCE_NOT_FOUND, 404",
            "INVALID_STATUS_TRANSITION, 409",
            "FILE_TOO_LARGE, 413",
            "FILE_INVALID_TYPE, 415",
            "PROFILE_INCOMPLETE, 422",
            "RATE_LIMITED, 429",
            "INTERNAL_ERROR, 500",
            "AI_INVALID_RESPONSE, 502",
            "AI_UNAVAILABLE, 503"
    })
    void statusesMatchTheCatalogue(ErrorCode code, int expectedStatus) {
        assertThat(ErrorCatalog.status(code).value()).isEqualTo(expectedStatus);
    }
}
