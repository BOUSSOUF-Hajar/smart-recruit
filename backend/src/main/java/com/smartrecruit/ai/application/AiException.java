package com.smartrecruit.ai.application;

import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;

import java.util.Map;

/** Échec IA porteur d'un code du catalogue : AI_DISABLED, AI_UNAVAILABLE, AI_INVALID_RESPONSE, FILE_UNREADABLE. */
public class AiException extends DomainException {

    public AiException(ErrorCode code, String message) {
        super(code, message, Map.of());
    }

    public static AiException disabled() {
        return new AiException(ErrorCode.AI_DISABLED, "L'assistance IA est désactivée.");
    }

    public static AiException unavailable(String message) {
        return new AiException(ErrorCode.AI_UNAVAILABLE, message);
    }

    public static AiException invalidResponse(String message) {
        return new AiException(ErrorCode.AI_INVALID_RESPONSE, message);
    }

    public static AiException unreadable() {
        return new AiException(ErrorCode.FILE_UNREADABLE, "CV non analysable automatiquement.");
    }
}
