package com.smartrecruit.shared.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    record Link(@HttpsUrl String url) {
    }

    @ChronologicalOrder(start = "start", end = "end")
    record Period(LocalDate start, LocalDate end) {
    }

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/salma", "https://www.linkedin.com/in/salma-idrissi"})
    void httpsLinksAreAccepted(String url) {
        assertThat(validator.validate(new Link(url))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://github.com/salma", "javascript:alert(1)", "https://localhost", "github.com",
            "HTTPS://github.com", "https://exa mple.com", "data:text/html,<b>x</b>"})
    void otherLinksAreRefused(String url) {
        assertThat(validator.validate(new Link(url))).hasSize(1);
    }

    @Test
    void endBeforeStartIsReportedOnTheEndField() {
        Set<ConstraintViolation<Period>> violations =
                validator.validate(new Period(LocalDate.of(2022, 3, 1), LocalDate.of(2021, 1, 1)));

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("end"));
    }

    @Test
    void openEndedOrOrderedPeriodsAreAccepted() {
        assertThat(validator.validate(new Period(LocalDate.of(2022, 3, 1), null))).isEmpty();
        assertThat(validator.validate(new Period(LocalDate.of(2022, 3, 1), LocalDate.of(2022, 3, 1)))).isEmpty();
    }
}
