package com.smartrecruit.application.application;

import com.smartrecruit.application.api.InterviewHistory;
import com.smartrecruit.application.domain.HistoryEventType;
import com.smartrecruit.application.infrastructure.HistoryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Implémente {@link InterviewHistory} : l'entrée rejoint la transaction de l'appelant. */
@Component
class InterviewHistoryRecorder implements InterviewHistory {

    private final HistoryRepository history;

    InterviewHistoryRecorder(HistoryRepository history) {
        this.history = history;
    }

    @Override
    public void record(UUID applicationId, InterviewEvent event, UUID actorId, Map<String, Object> details,
                       Instant at) {
        HistoryEventType type = switch (event) {
            case SCHEDULED -> HistoryEventType.INTERVIEW_SCHEDULED;
            case RESCHEDULED -> HistoryEventType.INTERVIEW_RESCHEDULED;
            case CANCELLED -> HistoryEventType.INTERVIEW_CANCELLED;
            case COMPLETED -> HistoryEventType.INTERVIEW_COMPLETED;
        };
        history.append(applicationId, type, null, null, actorId, details, at);
    }
}
