package com.smartrecruit.candidate.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-14 : du plus récent au plus ancien, postes en cours en tête. */
class SectionsOrderingTest {

    private static final UUID CANDIDATE = UUID.randomUUID();

    @Test
    void currentExperienceComesFirstThenMostRecentEnd() {
        Experience old = experience("Stagiaire", "2019-02-01", "2019-07-31");
        Experience current = experience("Développeuse", "2022-03-01", null);
        Experience previous = experience("Développeuse junior", "2019-09-01", "2022-02-28");

        List<Experience> sorted = List.of(old, current, previous).stream()
                .sorted(Experience.MOST_RECENT_FIRST).toList();

        assertThat(sorted).extracting(Experience::getJobTitle)
                .containsExactly("Développeuse", "Développeuse junior", "Stagiaire");
        assertThat(sorted.getFirst().isCurrent()).isTrue();
    }

    @Test
    void endBeforeStartIsRefused() {
        assertThatThrownBy(() -> experience("X", "2022-03-01", "2021-01-01"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void educationsAreSortedByEndYear() {
        Education master = Education.create(CANDIDATE, new EducationDetails("Master", "ENSIAS", null, 2020, 2022));
        Education licence = Education.create(CANDIDATE, new EducationDetails("Licence", "FST", null, 2017, 2020));
        Education ongoing = Education.create(CANDIDATE, new EducationDetails("Doctorat", "UM5", null, 2023, null));

        assertThat(List.of(licence, master, ongoing).stream().sorted(Education.MOST_RECENT_FIRST).toList())
                .extracting(Education::getDegree)
                .containsExactly("Doctorat", "Master", "Licence");
    }

    private static Experience experience(String title, String start, String end) {
        return Experience.create(CANDIDATE, new ExperienceDetails(title, "Agence X", null, LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end), null));
    }
}
