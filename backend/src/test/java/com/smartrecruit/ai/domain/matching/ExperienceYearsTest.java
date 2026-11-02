package com.smartrecruit.ai.domain.matching;

import com.smartrecruit.ai.domain.matching.ExperienceYears.Period;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** §5.1 : années déclarées d'abord, sinon somme des expériences sans compter deux fois les chevauchements. */
class ExperienceYearsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void declaredYearsWin() {
        assertThat(ExperienceYears.of(4, List.of(new Period(LocalDate.of(2010, 1, 1), null)), TODAY)).isEqualTo(4.0);
    }

    @Test
    void overlappingPeriodsAreCountedOnce() {
        Double years = ExperienceYears.of(null, List.of(
                new Period(LocalDate.of(2018, 1, 1), LocalDate.of(2021, 1, 1)),
                new Period(LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1)),   // chevauche d'un an
                new Period(LocalDate.of(2024, 1, 1), null)), TODAY);              // poste en cours

        assertThat(years).isCloseTo(4 + 33 / 12.0, within(0.01));   // 2018-2022 puis janv. 2024 → oct. 2026
    }

    @Test
    void nothingKnownGivesNull() {
        assertThat(ExperienceYears.of(null, List.of(), TODAY)).isNull();
    }
}
