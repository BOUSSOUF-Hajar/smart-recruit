package com.smartrecruit.shared.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.smartrecruit.shared.domain.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * Corps de toute réponse d'erreur, au format Problem Details (RFC 9457) étendu par le contrat de l'API :
 * {@code code}, {@code correlationId}, {@code errors}, plus d'éventuelles informations métier
 * au premier niveau (ex. {@code missing} pour PROFILE_INCOMPLETE).
 * <p>
 * Une classe dédiée plutôt que {@code ProblemDetail} de Spring : la forme exacte du JSON est
 * celle de la spécification, sans dépendre de la configuration de Jackson.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "title", "status", "detail", "instance", "code", "correlationId", "errors"})
public final class ApiProblem {

    private final ErrorCode code;
    private final int status;
    private final String detail;
    private final String instance;
    private final String correlationId;
    private final List<FieldViolation> errors;
    private final Map<String, Object> extensions;

    public ApiProblem(ErrorCode code, int status, String detail, String instance, String correlationId,
                      List<FieldViolation> errors, Map<String, Object> extensions) {
        this.code = code;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.correlationId = correlationId;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }

    public String getType() {
        return ErrorCatalog.type(code);
    }

    public String getTitle() {
        return ErrorCatalog.title(code);
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    /** Toujours présent (liste vide hors VALIDATION_FAILED) : le frontend n'a pas à tester son existence. */
    public List<FieldViolation> getErrors() {
        return errors;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /** Erreur sur un champ : {@code {"field": "email", "message": "doit être une adresse valide"}}. */
    public record FieldViolation(String field, String message) {
    }
}
