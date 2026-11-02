package com.smartrecruit.application.api;

/**
 * Statut affiché au candidat (cahier des charges D4.2). Calculé par le serveur : si la
 * correspondance était faite par le frontend, la réponse brute contiendrait SHORTLISTED.
 * Dans l'API du module : le tableau de bord candidat (module Tableaux de bord) l'utilise aussi.
 */
public enum CandidateStatus {

    SENT, IN_REVIEW, INTERVIEW, OFFER_RECEIVED, HIRED, NOT_SELECTED, WITHDRAWN;

    public static CandidateStatus of(ApplicationStatus status) {
        return switch (status) {
            case SUBMITTED -> SENT;
            case SCREENING, SHORTLISTED -> IN_REVIEW;
            case INTERVIEW -> INTERVIEW;
            case OFFER -> OFFER_RECEIVED;
            case HIRED -> HIRED;
            case REJECTED -> NOT_SELECTED;
            case WITHDRAWN -> WITHDRAWN;
        };
    }
}
