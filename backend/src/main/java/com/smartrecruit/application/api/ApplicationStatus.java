package com.smartrecruit.application.api;

import java.util.EnumSet;
import java.util.Set;

/**
 * Statuts d'une candidature et transitions de la table D4.1 du cahier des charges (RG-13, RG-16) :
 * <pre>
 * SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER → HIRED
 * tout statut non final → REJECTED (recruteur, motif) ou WITHDRAWN (candidat)
 * </pre>
 * 15 transitions sur les 64 couples possibles. Qui a le droit de les déclencher et sous quelles
 * conditions (motif, entretien terminé) est vérifié par {@code ApplicationWorkflow}.
 * <p>
 * Dans l'API du module parce que le module Entretiens lira ces statuts (RG-21, RG-22).
 */
public enum ApplicationStatus {

    SUBMITTED, SCREENING, SHORTLISTED, INTERVIEW, OFFER, HIRED, REJECTED, WITHDRAWN;

    /** Statuts atteignables directement depuis celui-ci, tous acteurs confondus. Vide pour un statut final. */
    public Set<ApplicationStatus> allowedTargets() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(SCREENING, REJECTED, WITHDRAWN);
            case SCREENING -> EnumSet.of(SHORTLISTED, REJECTED, WITHDRAWN);
            case SHORTLISTED -> EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN);
            case INTERVIEW -> EnumSet.of(OFFER, REJECTED, WITHDRAWN);
            case OFFER -> EnumSet.of(HIRED, REJECTED, WITHDRAWN);
            case HIRED, REJECTED, WITHDRAWN -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canMoveTo(ApplicationStatus target) {
        return allowedTargets().contains(target);
    }

    /** RG-16 : HIRED, REJECTED et WITHDRAWN sont définitifs. */
    public boolean isFinal() {
        return allowedTargets().isEmpty();
    }
}
