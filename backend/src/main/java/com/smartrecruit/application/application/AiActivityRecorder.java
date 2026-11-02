package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationAiRecorder;
import com.smartrecruit.application.domain.HistoryEventType;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.application.infrastructure.HistoryRepository;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Implémente {@link ApplicationAiRecorder} ; rejoint la transaction du module IA. */
@Component
class AiActivityRecorder implements ApplicationAiRecorder {

    private final HistoryRepository history;
    private final ApplicationRepository applications;

    AiActivityRecorder(HistoryRepository history, ApplicationRepository applications) {
        this.history = history;
        this.applications = applications;
    }

    @Override
    @Transactional
    public void analysisRequested(UUID applicationId, UUID actorId, String taskType, UUID taskId, Instant at) {
        history.append(applicationId, HistoryEventType.AI_ANALYSIS_REQUESTED, null, null, actorId,
                Map.of("taskType", taskType, "taskId", taskId.toString()), at);
    }

    @Override
    @Transactional
    public void matchScored(UUID applicationId, int score, Instant at) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score hors de 0 à 100 : " + score);
        }
        if (applications.recordMatchScore(applicationId, (short) score, at) == 0) {
            throw new ResourceNotFoundException("Candidature");
        }
    }
}
