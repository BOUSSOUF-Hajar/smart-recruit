package com.smartrecruit.shared.domain;

import java.util.Map;

/** Action impossible dans l'état actuel de la ressource (ex. INVALID_STATUS_TRANSITION, INVALID_STATE). */
public class InvalidStateException extends DomainException {

    public InvalidStateException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public InvalidStateException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
