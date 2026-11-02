package com.smartrecruit.ai.application;

import com.smartrecruit.ai.domain.AiTask;
import com.smartrecruit.ai.domain.AiTaskStatus;
import com.smartrecruit.ai.domain.AiTaskType;
import com.smartrecruit.ai.infrastructure.AiResultRepository;
import com.smartrecruit.ai.infrastructure.AiResultRepository.StoredAnalysis;
import com.smartrecruit.ai.infrastructure.AiResultRepository.StoredMatch;
import com.smartrecruit.ai.infrastructure.AiTaskRepository;
import com.smartrecruit.application.api.ApplicationAiRecorder;
import com.smartrecruit.application.api.ApplicationDirectory;
import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.shared.domain.RateLimitExceededException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.SlidingWindowRateLimiter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Demandes et lectures IA (US-41, US-42). Une demande crée une tâche PENDING et répond aussitôt
 * (202) ; le travail se fait en arrière-plan ({@link AiTaskWorker}). Chaque demande est auditée et
 * inscrite à l'historique de la candidature (US-43, RG-27).
 */
@Service
public class AiTaskService {

    static final String AI_ANALYSIS_REQUESTED = "AI_ANALYSIS_REQUESTED";
    private static final EnumSet<AiTaskStatus> ACTIVE = EnumSet.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING);

    private final AiTaskRepository tasks;
    private final AiResultRepository results;
    private final ApplicationDirectory applications;
    private final ApplicationAiRecorder recorder;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final AiProperties properties;
    private final SlidingWindowRateLimiter rateLimiter;
    private final Clock clock;

    public AiTaskService(AiTaskRepository tasks, AiResultRepository results, ApplicationDirectory applications,
                         ApplicationAiRecorder recorder, AuditRecorder audit, ApplicationEventPublisher events,
                         AiProperties properties, Clock clock) {
        this.tasks = tasks;
        this.results = results;
        this.applications = applications;
        this.recorder = recorder;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
        this.rateLimiter = new SlidingWindowRateLimiter(properties.requestsPerMinute(), Duration.ofMinutes(1), clock);
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * US-41, US-42. Une tâche du même type déjà en cours pour ce dossier est renvoyée au lieu d'en
     * créer une seconde (double clic) ; sinon une nouvelle tâche, même si un résultat existe déjà
     * (« relancer », F-AI-02 A4).
     */
    @Transactional
    public AiTask request(CurrentUser recruiter, UUID applicationId, AiTaskType type) {
        if (!properties.enabled()) {
            throw AiException.disabled();
        }
        ApplicationRef application = owned(recruiter, applicationId);
        var running = tasks.findFirstByApplicationIdAndTypeAndStatusInOrderByCreatedAtDesc(applicationId, type, ACTIVE);
        if (running.isPresent()) {
            return running.get();
        }
        String key = recruiter.id().toString();
        rateLimiter.blockedFor(key).ifPresent(wait -> {
            throw new RateLimitExceededException(wait);
        });
        rateLimiter.record(key);
        Instant now = clock.instant();
        AiTask task = tasks.saveAndFlush(AiTask.request(type, applicationId, application.companyId(),
                application.resumeId(), recruiter.id(), now));
        recorder.analysisRequested(applicationId, recruiter.id(), type.name(), task.getId(), now);
        audit.record(AuditEvent.success(AI_ANALYSIS_REQUESTED).by(recruiter.id(), null)
                .on("APPLICATION", applicationId).with("taskType", type.name()).with("taskId", task.getId()));
        events.publishEvent(new AiTaskCreated(task.getId()));
        return task;
    }

    @Transactional(readOnly = true)
    public AiTask task(CurrentUser recruiter, UUID taskId) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Tâche");
        }
        return tasks.findByIdAndCompanyId(taskId, recruiter.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tâche"));
    }

    /** Dernière analyse du CV joint à la candidature (la version figée à l'envoi, RG-12). */
    @Transactional(readOnly = true)
    public StoredAnalysis latestAnalysis(CurrentUser recruiter, UUID applicationId) {
        ApplicationRef application = owned(recruiter, applicationId);
        return results.latestAnalysis(application.resumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Analyse"));
    }

    @Transactional(readOnly = true)
    public StoredMatch latestMatch(CurrentUser recruiter, UUID applicationId) {
        owned(recruiter, applicationId);
        return results.latestMatch(applicationId).orElseThrow(() -> new ResourceNotFoundException("Matching"));
    }

    private ApplicationRef owned(CurrentUser recruiter, UUID applicationId) {
        return applications.find(applicationId)
                .filter(found -> recruiter.isRecruiterOf(found.companyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Candidature"));
    }
}
