package com.smartrecruit.application.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Résumé d'un entretien, sans lieu ni avis : il peut figurer dans une réponse destinée au candidat.
 *
 * @param type   PHONE, VIDEO, ON_SITE ou TECHNICAL
 * @param status SCHEDULED, COMPLETED ou CANCELLED
 */
public record InterviewBrief(UUID id, Instant startsAt, int durationMinutes, String type, String status) {
}
