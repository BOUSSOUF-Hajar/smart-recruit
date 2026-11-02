package com.smartrecruit.job.infrastructure;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** US-25 : liste blanche des tris. */
class JobSortTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            'publishedAt,desc', PUBLISHED_AT_DESC
            'publishedAt,asc',  PUBLISHED_AT_ASC
            'publishedAt',      PUBLISHED_AT_DESC
            'salaryMax,desc',   SALARY_MAX_DESC
            'salaryMax,ASC',    SALARY_MAX_ASC
            """)
    void knownSortsAreRecognised(String value, JobSort expected) {
        assertThat(JobSort.parse(value)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"title,asc", "publishedAt,sideways", "salary_max desc; DROP TABLE job_offer", ""})
    void anythingElseIsIgnored(String value) {
        assertThat(JobSort.parse(value)).isEmpty();
    }
}
