package com.smartrecruit.interview.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.ApplicationStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * RG-22 : une candidature rejetée ou retirée n'a plus d'entretien planifié. {@code @EventListener}
 * synchrone : l'annulation réussit ou échoue avec le changement de statut.
 */
@Component
class ApplicationOutcomeListener {

    private final InterviewService interviews;

    ApplicationOutcomeListener(InterviewService interviews) {
        this.interviews = interviews;
    }

    @EventListener
    void on(ApplicationStatusChanged event) {
        if (!event.endsTheProcess()) {
            return;
        }
        String reason = event.to() == ApplicationStatus.REJECTED
                ? "Candidature rejetée" : "Candidature retirée par le candidat";
        interviews.cancelScheduled(event.applicationId(), event.actorId(), reason, event.occurredAt());
    }
}
