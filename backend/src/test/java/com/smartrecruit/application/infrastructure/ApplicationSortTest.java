package com.smartrecruit.application.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-30 : tri en liste blanche, jamais par score sans le demander (RG-25). */
class ApplicationSortTest {

    @Test
    void unknownOrMissingSortFallsBackToTheMostRecent() {
        assertThat(ApplicationSort.parse(null)).isEqualTo(ApplicationSort.SUBMITTED_AT);
        assertThat(ApplicationSort.parse("candidate_id; DROP TABLE application")).isEqualTo(ApplicationSort.SUBMITTED_AT);
    }

    @Test
    void scoreSortIsOnlyUsedWhenAsked() {
        assertThat(ApplicationSort.parse("lastMatchScore,desc")).isEqualTo(ApplicationSort.LAST_MATCH_SCORE);
        assertThat(ApplicationSort.LAST_MATCH_SCORE.orderBy()).contains("NULLS LAST");
    }
}
