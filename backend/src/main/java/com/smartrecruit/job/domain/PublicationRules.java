package com.smartrecruit.job.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Conditions de publication (RG-19, RG-20) et calcul de l'instant d'expiration (analyse 6.2) :
 * l'offre reste ouverte jusqu'à 23:59:59 le jour choisi, dans le fuseau de l'entreprise.
 */
public final class PublicationRules {

    public static final int MAX_DAYS = 180;
    static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private PublicationRules() {
    }

    public static Instant expiryInstant(LocalDate expiryDate, ZoneId zone) {
        return expiryDate.atTime(END_OF_DAY).atZone(zone).toInstant();
    }

    /** Raisons qui empêchent la publication ; liste vide si l'offre peut être publiée. */
    static List<String> blockingReasons(LocalDate expiryDate, ZoneId zone, Instant now, boolean hasRequiredSkill) {
        List<String> reasons = new ArrayList<>();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (!expiryInstant(expiryDate, zone).isAfter(now)) {
            reasons.add("EXPIRY_IN_PAST");
        } else if (expiryDate.isAfter(today.plusDays(MAX_DAYS))) {
            reasons.add("EXPIRY_TOO_FAR");
        }
        if (!hasRequiredSkill) {
            reasons.add("NO_REQUIRED_SKILL");
        }
        return reasons;
    }
}
