package com.smartrecruit.candidate.api;

import java.time.Instant;
import java.util.List;

/**
 * Résumé du profil pour le tableau de bord du candidat (US-45) : complétude (analyse fonctionnelle
 * §6.1) et CV actif.
 *
 * @param missingCriteria critères de complétude manquants, codes de la grille (le frontend mène au bon onglet)
 * @param resumeUploadedAt date du CV actif, ou {@code null} s'il n'y en a pas
 */
public record ProfileOverview(int completenessPercent, List<String> missingCriteria, boolean resumePresent,
                              Instant resumeUploadedAt) {

    public ProfileOverview {
        missingCriteria = List.copyOf(missingCriteria);
    }
}
