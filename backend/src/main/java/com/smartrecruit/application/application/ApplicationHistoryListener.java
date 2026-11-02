package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.ApplicationStatusChanged;
import com.smartrecruit.application.api.ApplicationSubmitted;
import com.smartrecruit.application.domain.HistoryEventType;
import com.smartrecruit.application.infrastructure.HistoryRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Écrit l'historique (US-34) à partir des événements du module. {@code @EventListener} synchrone :
 * l'entrée est insérée dans la transaction de l'action ; si l'une échoue, l'autre n'existe pas
 * (RG-24). Un écouteur asynchrone après commit pourrait perdre des entrées.
 */
@Component
class ApplicationHistoryListener {

    private final HistoryRepository history;

    ApplicationHistoryListener(HistoryRepository history) {
        this.history = history;
    }

    @EventListener
    void on(ApplicationSubmitted event) {
        history.append(event.applicationId(), HistoryEventType.CREATED, null, ApplicationStatus.SUBMITTED,
                event.candidateId(), Map.of(), event.occurredAt());
    }

    @EventListener
    void on(ApplicationStatusChanged event) {
        history.append(event.applicationId(), HistoryEventType.STATUS_CHANGED, event.from(), event.to(),
                event.actorId(), event.details(), event.occurredAt());
    }
}
