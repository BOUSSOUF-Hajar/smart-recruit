package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.domain.TransitionContext.Actor;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Règles du workflow de candidature (analyse fonctionnelle §3.4), sans base ni Spring : le service
 * orchestre, il ne contient aucune suite de {@code if} sur les statuts.
 * <ol>
 *   <li>la transition existe dans la table D4.1 (RG-13), sinon {@code INVALID_STATUS_TRANSITION} ;</li>
 *   <li>WITHDRAWN est réservé au candidat, tout le reste au recruteur (même code) ;</li>
 *   <li>REJECTED exige un motif (RG-14) : {@code REJECTION_REASON_REQUIRED} ;</li>
 *   <li>OFFER exige un entretien terminé (RG-33) : {@code OFFER_REQUIRES_COMPLETED_INTERVIEW}.</li>
 * </ol>
 */
public final class ApplicationWorkflow {

    private ApplicationWorkflow() {
    }

    public static void check(ApplicationStatus from, ApplicationStatus to, TransitionContext context) {
        if (!from.canMoveTo(to)) {
            throw invalid(from, to, from.isFinal()
                    ? "Cette candidature est dans un état définitif."
                    : "Passage de " + from + " à " + to + " impossible.");
        }
        if ((to == ApplicationStatus.WITHDRAWN) != (context.actor() == Actor.CANDIDATE)) {
            throw invalid(from, to, to == ApplicationStatus.WITHDRAWN
                    ? "Seul le candidat peut retirer sa candidature."
                    : "Le candidat peut seulement retirer sa candidature.");
        }
        if (to == ApplicationStatus.REJECTED && context.rejectionReason() == null) {
            throw new BusinessRuleViolationException(ErrorCode.REJECTION_REASON_REQUIRED,
                    "Un rejet exige un motif.");
        }
        if (to == ApplicationStatus.OFFER && !context.hasCompletedInterview()) {
            throw new InvalidStateException(ErrorCode.OFFER_REQUIRES_COMPLETED_INTERVIEW,
                    "Une proposition exige au moins un entretien terminé.");
        }
    }

    /**
     * Transitions à proposer à un recruteur : celles de la table, sans WITHDRAWN, et sans OFFER tant
     * qu'aucun entretien n'est terminé. Le frontend affiche cette liste sans dupliquer la règle.
     */
    public static Set<ApplicationStatus> recruiterChoices(ApplicationStatus from, boolean hasCompletedInterview) {
        Set<ApplicationStatus> choices = EnumSet.noneOf(ApplicationStatus.class);
        choices.addAll(from.allowedTargets());
        choices.remove(ApplicationStatus.WITHDRAWN);
        if (!hasCompletedInterview) {
            choices.remove(ApplicationStatus.OFFER);
        }
        return choices;
    }

    /** RG-15 : le candidat peut retirer tant que la candidature n'est pas finale. */
    public static boolean candidateCanWithdraw(ApplicationStatus status) {
        return status.canMoveTo(ApplicationStatus.WITHDRAWN);
    }

    private static InvalidStateException invalid(ApplicationStatus from, ApplicationStatus to, String message) {
        return new InvalidStateException(ErrorCode.INVALID_STATUS_TRANSITION, message,
                Map.of("currentStatus", from.name(), "targetStatus", to.name()));
    }
}
