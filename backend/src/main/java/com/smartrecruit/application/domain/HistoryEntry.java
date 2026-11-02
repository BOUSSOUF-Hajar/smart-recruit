package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entrée immuable de l'historique d'une candidature (RG-24). Un record, pas une entité : il n'existe
 * aucun moyen de la modifier, ni en Java ni en SQL (déclencheur {@code trg_application_event_immutable}).
 *
 * @param actorId {@code null} si l'action vient du système
 */
public record HistoryEntry(UUID id, UUID applicationId, HistoryEventType type, ApplicationStatus fromStatus,
                           ApplicationStatus toStatus, UUID actorId, Map<String, Object> details,
                           Instant occurredAt) {

    public HistoryEntry {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
