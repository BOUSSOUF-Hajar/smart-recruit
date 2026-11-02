package com.smartrecruit.job.infrastructure;

import java.util.Locale;
import java.util.Optional;

/**
 * Tris autorisés de la recherche publique (liste blanche). La valeur reçue n'est jamais recopiée
 * dans le SQL : seul l'un de ces tris connus peut y entrer, ce qui exclut toute injection.
 */
public enum JobSort {
    PUBLISHED_AT_DESC("j.published_at DESC"),
    PUBLISHED_AT_ASC("j.published_at ASC"),
    SALARY_MAX_DESC("(CASE WHEN j.salary_visible THEN j.salary_max END) DESC NULLS LAST"),
    SALARY_MAX_ASC("(CASE WHEN j.salary_visible THEN j.salary_max END) ASC NULLS LAST");

    public static final JobSort DEFAULT = PUBLISHED_AT_DESC;

    private final String orderBy;

    JobSort(String orderBy) {
        this.orderBy = orderBy;
    }

    String orderBy() {
        return orderBy;
    }

    /** {@code publishedAt,desc} ou {@code salaryMax,asc} ; toute autre valeur est ignorée. */
    public static Optional<JobSort> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split(",");
        String direction = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "desc";
        if (!direction.equals("asc") && !direction.equals("desc")) {
            return Optional.empty();
        }
        boolean ascending = direction.equals("asc");
        return switch (parts[0].strip()) {
            case "publishedAt" -> Optional.of(ascending ? PUBLISHED_AT_ASC : PUBLISHED_AT_DESC);
            case "salaryMax" -> Optional.of(ascending ? SALARY_MAX_ASC : SALARY_MAX_DESC);
            default -> Optional.empty();
        };
    }
}
