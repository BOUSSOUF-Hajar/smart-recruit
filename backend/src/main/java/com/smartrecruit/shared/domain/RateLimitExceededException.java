package com.smartrecruit.shared.domain;

import java.time.Duration;
import java.util.Map;

/** Trop de tentatives sur une période : la requête est refusée avant tout traitement (ENF-SEC-09). */
public class RateLimitExceededException extends DomainException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, "Trop de tentatives. Réessayez dans quelques minutes.",
                Map.of("retryAfterSeconds", retryAfter.toSeconds()));
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
