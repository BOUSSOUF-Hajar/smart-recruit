package com.smartrecruit.application.domain;

/**
 * Ce que les règles de transition doivent savoir en plus des deux statuts.
 *
 * @param actor                 qui demande le changement
 * @param rejectionReason       motif, obligatoire pour REJECTED (RG-14)
 * @param hasCompletedInterview au moins un entretien terminé (RG-33)
 */
public record TransitionContext(Actor actor, RejectionReason rejectionReason, boolean hasCompletedInterview) {

    public enum Actor { RECRUITER, CANDIDATE }

    public static TransitionContext recruiter(RejectionReason reason, boolean hasCompletedInterview) {
        return new TransitionContext(Actor.RECRUITER, reason, hasCompletedInterview);
    }

    public static TransitionContext candidate() {
        return new TransitionContext(Actor.CANDIDATE, null, false);
    }
}
