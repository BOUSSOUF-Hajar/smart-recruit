package com.smartrecruit.shared.domain;

import java.util.List;
import java.util.Map;

/**
 * Champ refusé par une vérification que Bean Validation ne peut pas faire seule : elle demande
 * la base (compétence inconnue) ou une vue d'ensemble de la liste (doublon dans une liste).
 * <p>
 * Même réponse qu'une erreur d'annotation : {@code 400 VALIDATION_FAILED} avec le champ en erreur,
 * que le frontend affiche sous le bon contrôle.
 */
public class FieldValidationException extends DomainException {

    private final List<FieldError> errors;

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public FieldValidationException(List<FieldError> errors) {
        super(ErrorCode.VALIDATION_FAILED, "Un ou plusieurs champs sont invalides.", Map.of());
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Au moins une erreur de champ est attendue");
        }
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    /** @param field chemin du champ, comme Bean Validation l'écrit : {@code skills[2].skillId} */
    public record FieldError(String field, String message) {
    }
}
