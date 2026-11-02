package com.smartrecruit.candidate.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-20 : grille de l'analyse fonctionnelle §6.1, total 100. */
class CompletenessTest {

    @Test
    void newProfileOnlyHasTheName() {
        Completeness completeness = Completeness.of(
                new Completeness.Facts("Salma", "Idrissi", null, null, 0, 0, 0, 0, false));

        assertThat(completeness.percent()).isEqualTo(10);
        assertThat(completeness.missing()).containsExactly(
                "headline", "summary", "experience", "education", "skills", "language", "resume");
    }

    @Test
    void completeProfileReaches100() {
        Completeness completeness = Completeness.of(
                new Completeness.Facts("Salma", "Idrissi", "Développeuse Java", "Résumé", 2, 1, 3, 1, true));

        assertThat(completeness.percent()).isEqualTo(100);
        assertThat(completeness.missing()).isEmpty();
    }

    @Test
    void skillsCountOnlyFromThree() {
        Completeness two = Completeness.of(new Completeness.Facts("A", "B", "T", "R", 1, 1, 2, 1, true));

        assertThat(two.percent()).isEqualTo(85);
        assertThat(two.missing()).containsExactly("skills");
    }

    @Test
    void blankValuesDoNotCount() {
        Completeness completeness = Completeness.of(new Completeness.Facts("Salma", " ", "  ", "", 0, 0, 0, 0, false));

        assertThat(completeness.percent()).isZero();
        assertThat(completeness.missing()).startsWith("name", "headline", "summary");
    }
}
