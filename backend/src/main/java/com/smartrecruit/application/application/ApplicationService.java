package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.ApplicationStatusChanged;
import com.smartrecruit.application.api.ApplicationSubmitted;
import com.smartrecruit.application.api.CompletedInterviewChecker;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.domain.StatusChange;
import com.smartrecruit.application.domain.TransitionContext;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.candidate.api.ApplicationReadiness;
import com.smartrecruit.candidate.api.CandidateDirectory;
import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobReference;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Écritures sur les candidatures : postuler (US-27), retirer (US-29), changer de statut (US-32, US-33).
 * Le service orchestre (droits, chargement, persistance, événements) ; les règles de transition sont
 * dans {@link com.smartrecruit.application.domain.ApplicationWorkflow}.
 * <p>
 * Chaque changement publie un événement dans la transaction : l'historique (et, en phase 7,
 * l'annulation des entretiens) est écrit avec lui ou pas du tout.
 */
@Service
public class ApplicationService {

    static final String APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    static final String APPLICATION_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    private final ApplicationRepository applications;
    private final ApplicationAccess access;
    private final JobCatalog jobs;
    private final CandidateDirectory candidates;
    private final ObjectProvider<CompletedInterviewChecker> interviews;
    private final ApplicationEventPublisher events;
    private final AuditRecorder audit;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, ApplicationAccess access, JobCatalog jobs,
                              CandidateDirectory candidates, ObjectProvider<CompletedInterviewChecker> interviews,
                              ApplicationEventPublisher events, AuditRecorder audit, Clock clock) {
        this.applications = applications;
        this.access = access;
        this.jobs = jobs;
        this.candidates = candidates;
        this.interviews = interviews;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * US-27 (analyse fonctionnelle §3.3). Les vérifications préalables donnent un message clair ; la
     * garantie contre deux envois simultanés reste l'index {@code uk_application_job_candidate},
     * traduit en {@code APPLICATION_ALREADY_EXISTS} par {@code ApplicationConstraints}.
     */
    @Transactional
    public Application apply(CurrentUser candidate, UUID jobId) {
        JobReference job = jobs.find(jobId).orElseThrow(() -> new ResourceNotFoundException("Offre"));
        if (!job.open()) {
            throw new InvalidStateException(ErrorCode.JOB_NOT_OPEN, "Cette offre n'accepte plus de candidatures.");
        }
        applications.findByJobOfferIdAndCandidateId(jobId, candidate.id()).ifPresent(existing -> {
            throw new InvalidStateException(ErrorCode.APPLICATION_ALREADY_EXISTS,
                    "Vous avez déjà postulé à cette offre.", Map.of("applicationId", existing.getId()));
        });
        ApplicationReadiness readiness = candidates.readiness(candidate.id());
        if (!readiness.ready()) {
            throw new BusinessRuleViolationException(ErrorCode.PROFILE_INCOMPLETE,
                    "Complétez votre profil avant de postuler.", Map.of("missing", readiness.missing()));
        }
        Instant now = clock.instant();
        // saveAndFlush : une violation de l'index unique est levée ici, dans la transaction, et non au commit
        Application application = applications.saveAndFlush(
                Application.submit(jobId, candidate.id(), readiness.activeResumeId(), now));
        events.publishEvent(new ApplicationSubmitted(application.getId(), jobId, candidate.id(), now));
        audit.record(AuditEvent.success(APPLICATION_SUBMITTED).by(candidate.id(), null)
                .on("APPLICATION", application.getId()).with("jobOfferId", jobId));
        return application;
    }

    /** US-29 : retrait par le candidat, possible tant que la candidature n'est pas finale (RG-15). */
    @Transactional
    public Application withdraw(CurrentUser candidate, UUID applicationId, String comment) {
        Application application = access.forCandidate(candidate, applicationId);
        return transition(application, ApplicationStatus.WITHDRAWN, TransitionContext.candidate(), comment,
                candidate.id());
    }

    /** US-32, US-33. La version envoyée doit être celle lue : sinon 409, rien n'est modifié. */
    @Transactional
    public Application changeStatus(CurrentUser recruiter, UUID applicationId, ChangeStatus command) {
        Application application = access.forRecruiter(recruiter, applicationId).application();
        if (application.getVersion() == null || application.getVersion() != command.version()) {
            throw new OptimisticLockingFailureException("Candidature " + applicationId + " : version "
                    + command.version() + " obsolète (actuelle " + application.getVersion() + ")");
        }
        boolean completedInterview = command.target() == ApplicationStatus.OFFER && hasCompletedInterview(applicationId);
        return transition(application, command.target(),
                TransitionContext.recruiter(command.rejectionReason(), completedInterview), command.comment(),
                recruiter.id());
    }

    boolean hasCompletedInterview(UUID applicationId) {
        CompletedInterviewChecker checker = interviews.getIfAvailable();
        return checker != null && checker.hasCompletedInterview(applicationId);
    }

    private Application transition(Application application, ApplicationStatus target, TransitionContext context,
                                   String comment, UUID actorId) {
        Instant now = clock.instant();
        StatusChange change = application.moveTo(target, context, comment, now);
        // flush : la version est vérifiée maintenant (écriture concurrente → 409) et la réponse porte la nouvelle
        applications.flush();
        events.publishEvent(new ApplicationStatusChanged(application.getId(), application.getJobOfferId(),
                application.getCandidateId(), change.from(), change.to(), actorId,
                historyDetails(application, comment), now));
        audit.record(AuditEvent.success(APPLICATION_STATUS_CHANGED).by(actorId, null)
                .on("APPLICATION", application.getId())
                .with("from", change.from().name()).with("to", change.to().name()));
        return application;
    }

    /** Ce que l'historique garde de la transition ; jamais montré au candidat. */
    private static Map<String, Object> historyDetails(Application application, String comment) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            details.put("rejectionReason", application.getRejectionReason().name());
        }
        if (comment != null && !comment.isBlank()) {
            details.put("comment", comment.strip());
        }
        return details;
    }
}
