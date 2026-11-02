package com.smartrecruit.application.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inscription des événements d'entretien dans l'historique de la candidature (US-34 à US-37).
 * Implémenté par ce module, appelé par le module Entretiens dans sa transaction : l'entretien et son
 * entrée d'historique sont validés ensemble (RG-24).
 * <p>
 * Un appel direct plutôt qu'un événement écouté ici : Entretiens dépend déjà de Candidatures ;
 * écouter ses événements créerait la dépendance inverse, donc un cycle.
 */
public interface InterviewHistory {

    enum InterviewEvent { SCHEDULED, RESCHEDULED, CANCELLED, COMPLETED }

    /** @param actorId {@code null} si l'action vient du système */
    void record(UUID applicationId, InterviewEvent event, UUID actorId, Map<String, Object> details, Instant at);
}
