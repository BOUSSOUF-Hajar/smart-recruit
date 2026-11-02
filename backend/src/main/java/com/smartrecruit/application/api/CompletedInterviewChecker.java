package com.smartrecruit.application.api;

import java.util.UUID;

/**
 * RG-33 : le passage INTERVIEW → OFFER exige au moins un entretien terminé. Implémenté par le module
 * Entretiens (phase 7), qui dépend des Candidatures et non l'inverse. Tant qu'aucune implémentation
 * n'existe, aucune candidature n'a d'entretien terminé : OFFER et HIRED restent inaccessibles.
 */
public interface CompletedInterviewChecker {

    boolean hasCompletedInterview(UUID applicationId);
}
