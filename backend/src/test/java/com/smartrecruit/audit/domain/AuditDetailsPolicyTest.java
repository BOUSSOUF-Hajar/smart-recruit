package com.smartrecruit.audit.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditDetailsPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"password", "newPassword", "accessToken", "refresh_token", "jwtSecret",
            "Authorization", "resumeText", "cv_content"})
    void refusesSensitiveKeys(String key) {
        assertThatThrownBy(() -> AuditDetailsPolicy.check(Map.of(key, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key);
    }

    @Test
    void inspectsNestedMapsAndLists() {
        assertThatThrownBy(() -> AuditDetailsPolicy.check(Map.of("changes", List.of(Map.of("oldPassword", "x")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsOrdinaryTechnicalDetails() {
        assertThatCode(() -> AuditDetailsPolicy.check(Map.of(
                "applicationId", "42", "fromStatus", "SCREENING", "contentType", "application/pdf")))
                .doesNotThrowAnyException();
    }
}
