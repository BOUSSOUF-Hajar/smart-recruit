package com.smartrecruit.identity.domain;

/** Pourquoi une session a été fermée ; seul {@link #ROTATED} rend suspecte une nouvelle présentation du jeton. */
public enum RevocationReason {
    /** Remplacé par un nouveau jeton lors d'un renouvellement (US-08). */
    ROTATED,
    LOGOUT,
    PASSWORD_CHANGED,
    /** Fermée parce qu'un jeton déjà tourné a été rejoué : vol présumé. */
    REUSE_DETECTED,
    ACCOUNT_DISABLED,
    /** L'admin a rattaché le recruteur à une autre entreprise (US-48). */
    COMPANY_CHANGED
}
