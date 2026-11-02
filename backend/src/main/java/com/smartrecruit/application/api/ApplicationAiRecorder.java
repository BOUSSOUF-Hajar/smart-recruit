package com.smartrecruit.application.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Traces de l'assistance IA sur une candidature (US-34, US-42). Implémenté par ce module, appelé par
 * le module IA dans sa transaction. Aucune méthode ne touche au statut : un résultat IA ne décide
 * jamais (RG-25).
 */
public interface ApplicationAiRecorder {

    /** Entrée AI_ANALYSIS_REQUESTED dans l'historique. */
    void analysisRequested(UUID applicationId, UUID actorId, String taskType, UUID taskId, Instant at);

    /** Copie du dernier score sur la candidature, pour filtrer la liste (la source reste le résultat IA). */
    void matchScored(UUID applicationId, int score, Instant at);
}
