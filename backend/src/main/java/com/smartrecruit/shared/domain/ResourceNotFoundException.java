package com.smartrecruit.shared.domain;

import java.util.Map;

/**
 * Ressource inexistante, ou hors du périmètre de l'appelant.
 * Les deux cas donnent la même réponse, pour ne jamais confirmer l'existence d'une ressource d'autrui.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resourceType) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceType + " introuvable.", Map.of());
    }
}
