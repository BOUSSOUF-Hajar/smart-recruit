package com.smartrecruit.shared.domain;

/**
 * Ressource visible de l'appelant, mais action réservée à quelqu'un d'autre (403 ACCESS_DENIED) :
 * modifier la note d'un collègue, clôturer un entretien sans y participer. Hors périmètre, on
 * répond 404 ({@link ResourceNotFoundException}) pour ne pas confirmer l'existence de la ressource.
 */
public class ForbiddenActionException extends DomainException {

    public ForbiddenActionException(String message) {
        super(ErrorCode.ACCESS_DENIED, message, java.util.Map.of());
    }
}
