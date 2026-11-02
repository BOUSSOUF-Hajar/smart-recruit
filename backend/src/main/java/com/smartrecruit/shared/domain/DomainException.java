package com.smartrecruit.shared.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Exception métier : porte un code du catalogue, un message destiné à l'utilisateur
 * et, si besoin, des informations structurées (ex. {@code missing} pour PROFILE_INCOMPLETE).
 * <p>
 * Elle ignore tout du protocole HTTP : la traduction en statut est faite par la couche web.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    protected DomainException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    /** Informations complémentaires exposées au client, au premier niveau de la réponse. */
    public Map<String, Object> details() {
        return details;
    }
}
