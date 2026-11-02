package com.smartrecruit.application.infrastructure;

import java.util.Locale;

/**
 * Tris autorisés de la liste recruteur, en liste blanche : la valeur reçue n'entre jamais dans le SQL.
 * Jamais trié par score par défaut (RG-25) : le score aide, il ne classe pas d'office.
 */
public enum ApplicationSort {

    SUBMITTED_AT("a.submitted_at DESC"),
    LAST_MATCH_SCORE("a.last_match_score DESC NULLS LAST, a.submitted_at DESC");

    private final String orderBy;

    ApplicationSort(String orderBy) {
        this.orderBy = orderBy;
    }

    String orderBy() {
        return orderBy;
    }

    /** {@code submittedAt}, {@code lastMatchScore} (suffixe {@code ,desc} toléré) ; tout le reste → défaut. */
    public static ApplicationSort parse(String value) {
        if (value == null) {
            return SUBMITTED_AT;
        }
        return switch (value.split(",")[0].strip().toLowerCase(Locale.ROOT)) {
            case "lastmatchscore" -> LAST_MATCH_SCORE;
            default -> SUBMITTED_AT;
        };
    }
}
