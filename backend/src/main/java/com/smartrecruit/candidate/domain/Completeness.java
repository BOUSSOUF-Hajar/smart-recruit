package com.smartrecruit.candidate.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Complétude du profil, grille de l'analyse fonctionnelle §6.1 (US-20). Informative : elle ne bloque
 * rien ; seul le profil minimal de RG-09 conditionne une candidature.
 *
 * @param missing critères non remplis, dans l'ordre de la grille ; le frontend choisit les libellés
 */
public record Completeness(int percent, List<String> missing) {

    public Completeness {
        missing = List.copyOf(missing);
    }

    /** Éléments du profil utiles au calcul, rassemblés depuis le compte, le profil et le CV. */
    public record Facts(String firstName, String lastName, String headline, String summary, int experiences,
                        int educations, int skills, int languages, boolean activeResume) {
    }

    public static Completeness of(Facts facts) {
        int percent = 0;
        List<String> missing = new ArrayList<>();
        percent += score(filled(facts.firstName()) && filled(facts.lastName()), 10, "name", missing);
        percent += score(filled(facts.headline()), 15, "headline", missing);
        percent += score(filled(facts.summary()), 10, "summary", missing);
        percent += score(facts.experiences() >= 1, 20, "experience", missing);
        percent += score(facts.educations() >= 1, 10, "education", missing);
        percent += score(facts.skills() >= 3, 15, "skills", missing);
        percent += score(facts.languages() >= 1, 5, "language", missing);
        percent += score(facts.activeResume(), 15, "resume", missing);
        return new Completeness(percent, missing);
    }

    private static int score(boolean met, int points, String criterion, List<String> missing) {
        if (met) {
            return points;
        }
        missing.add(criterion);
        return 0;
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
