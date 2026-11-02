package com.smartrecruit.shared.domain;

import java.util.Map;

/** Requête valide mais refusée par une règle métier (ex. PROFILE_INCOMPLETE, REJECTION_REASON_REQUIRED). */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessRuleViolationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
