package com.smartrecruit.candidate.api;

import java.time.Instant;
import java.util.UUID;

/** Métadonnées d'une version de CV ; jamais la clé de stockage. */
public record ResumeSummary(UUID id, UUID candidateId, String originalFilename, int sizeBytes, Instant uploadedAt,
                            boolean active) {
}
