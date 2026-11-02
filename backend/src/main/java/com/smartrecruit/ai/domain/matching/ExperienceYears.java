package com.smartrecruit.ai.domain.matching;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Années d'expérience du candidat (analyse fonctionnelle §5.1) : le champ déclaré du profil d'abord,
 * sinon la somme des expériences du profil sans compter deux fois les chevauchements.
 */
public final class ExperienceYears {

    private ExperienceYears() {
    }

    public record Period(LocalDate start, LocalDate end) {
    }

    /** @return années (décimales), ou {@code null} si rien ne permet de les connaître */
    public static Double of(Integer declared, List<Period> periods, LocalDate today) {
        if (declared != null) {
            return declared.doubleValue();
        }
        if (periods.isEmpty()) {
            return null;
        }
        List<Period> sorted = new ArrayList<>(periods.stream()
                .map(p -> new Period(p.start(), p.end() == null || p.end().isAfter(today) ? today : p.end()))
                .filter(p -> !p.end().isBefore(p.start()))
                .toList());
        sorted.sort(Comparator.comparing(Period::start));
        long months = 0;
        LocalDate currentStart = null;
        LocalDate currentEnd = null;
        for (Period period : sorted) {
            if (currentEnd == null || period.start().isAfter(currentEnd)) {
                if (currentEnd != null) {
                    months += ChronoUnit.MONTHS.between(currentStart, currentEnd);
                }
                currentStart = period.start();
                currentEnd = period.end();
            } else if (period.end().isAfter(currentEnd)) {
                currentEnd = period.end();
            }
        }
        if (currentEnd != null) {
            months += ChronoUnit.MONTHS.between(currentStart, currentEnd);
        }
        return months / 12.0;
    }
}
