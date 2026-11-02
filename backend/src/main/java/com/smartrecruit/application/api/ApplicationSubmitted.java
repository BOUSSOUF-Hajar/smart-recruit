package com.smartrecruit.application.api;

import java.time.Instant;
import java.util.UUID;

/** Une candidature vient d'être créée (US-27). Publié dans la transaction de création. */
public record ApplicationSubmitted(UUID applicationId, UUID jobOfferId, UUID candidateId, Instant occurredAt) {
}
