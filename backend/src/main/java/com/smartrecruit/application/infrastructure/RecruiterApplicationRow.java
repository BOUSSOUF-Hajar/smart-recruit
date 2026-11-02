package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.api.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

/** Ligne de la liste recruteur, déjà complète : aucune requête de plus par ligne. */
public record RecruiterApplicationRow(UUID id, UUID candidateId, String firstName, String lastName, String headline,
                                      UUID jobOfferId, String jobTitle, ApplicationStatus status,
                                      Instant submittedAt, Integer lastMatchScore) {
}
