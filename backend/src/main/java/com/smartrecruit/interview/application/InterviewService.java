package com.smartrecruit.interview.application;

import com.smartrecruit.application.api.ApplicationDirectory;
import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.InterviewHistory;
import com.smartrecruit.application.api.InterviewHistory.InterviewEvent;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.RecruiterSummary;
import com.smartrecruit.interview.domain.FeedbackRating;
import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.domain.InterviewPlan;
import com.smartrecruit.interview.domain.InterviewStatus;
import com.smartrecruit.interview.infrastructure.InterviewRepository;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Écritures sur les entretiens (US-35 à US-37) et annulation automatique (RG-22). Chaque action
 * inscrit son entrée dans l'historique de la candidature, dans la même transaction.
 */
@Service
public class InterviewService {

    private final InterviewRepository interviews;
    private final ApplicationDirectory applications;
    private final InterviewHistory history;
    private final CompanyDirectory companies;
    private final Clock clock;

    public InterviewService(InterviewRepository interviews, ApplicationDirectory applications,
                            InterviewHistory history, CompanyDirectory companies, Clock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.history = history;
        this.companies = companies;
        this.clock = clock;
    }

    /** US-35 : candidature de l'entreprise en INTERVIEW (RG-21), date future, participants de l'entreprise. */
    @Transactional
    public Interview schedule(CurrentUser recruiter, UUID applicationId, InterviewPlan plan) {
        ApplicationRef application = applications.find(applicationId)
                .filter(found -> recruiter.isRecruiterOf(found.companyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Candidature"));
        if (application.status() != ApplicationStatus.INTERVIEW) {
            throw new InvalidStateException(ErrorCode.INTERVIEW_NOT_ALLOWED,
                    "Un entretien se planifie pour une candidature au statut « Entretien ».");
        }
        Instant now = clock.instant();
        validate(recruiter, plan, now);
        Interview interview = interviews.saveAndFlush(Interview.schedule(applicationId, application.companyId(),
                application.jobOfferId(), recruiter.id(), plan));
        history.record(applicationId, InterviewEvent.SCHEDULED, recruiter.id(), details(interview, Map.of(
                "startsAt", interview.getStartsAt().toString(), "type", interview.getType().name())), now);
        return interview;
    }

    /** US-36 : ancienne et nouvelle date dans l'historique ; la version lue doit être la version actuelle. */
    @Transactional
    public Interview reschedule(CurrentUser recruiter, UUID id, InterviewPlan plan, long version) {
        Interview interview = load(recruiter, id);
        if (interview.getVersion() == null || interview.getVersion() != version) {
            throw new OptimisticLockingFailureException("Entretien " + id + " : version " + version + " obsolète");
        }
        interview.checkModifiable();   // INVALID_STATE avant toute validation de champ
        Instant now = clock.instant();
        validate(recruiter, plan, now);
        Instant previous = interview.reschedule(plan);
        interviews.flush();
        history.record(interview.getApplicationId(), InterviewEvent.RESCHEDULED, recruiter.id(), details(interview,
                Map.of("previousStartsAt", previous.toString(), "startsAt", interview.getStartsAt().toString())), now);
        return interview;
    }

    /** US-36 */
    @Transactional
    public Interview cancel(CurrentUser recruiter, UUID id, String reason) {
        Interview interview = load(recruiter, id);
        interview.cancel(reason);
        interviews.flush();
        history.record(interview.getApplicationId(), InterviewEvent.CANCELLED, recruiter.id(),
                details(interview, reason == null || reason.isBlank() ? Map.of() : Map.of("reason", reason.strip())),
                clock.instant());
        return interview;
    }

    /** US-37 : débloque le passage à OFFER (RG-33). */
    @Transactional
    public Interview complete(CurrentUser recruiter, UUID id, FeedbackRating rating, String comment) {
        Interview interview = load(recruiter, id);
        Instant now = clock.instant();
        interview.complete(recruiter.id(), rating, comment, now);
        interviews.flush();
        history.record(interview.getApplicationId(), InterviewEvent.COMPLETED, recruiter.id(),
                details(interview, Map.of("rating", rating.name())), now);
        return interview;
    }

    /**
     * RG-22 : la candidature est rejetée ou retirée ; ses entretiens planifiés sont annulés, dans la
     * transaction du changement de statut.
     */
    @Transactional
    public int cancelScheduled(UUID applicationId, UUID actorId, String reason, Instant at) {
        var scheduled = interviews.findByApplicationIdAndStatus(applicationId, InterviewStatus.SCHEDULED);
        for (Interview interview : scheduled) {
            interview.cancel(reason);
            history.record(applicationId, InterviewEvent.CANCELLED, actorId,
                    details(interview, Map.of("reason", reason, "automatic", true)), at);
        }
        interviews.flush();
        return scheduled.size();
    }

    private Interview load(CurrentUser recruiter, UUID id) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Entretien");
        }
        return interviews.findByIdAndCompanyId(id, recruiter.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Entretien"));
    }

    /** Règles du plan, puis participants : tous recruteurs de l'entreprise de l'appelant. */
    private void validate(CurrentUser recruiter, InterviewPlan plan, Instant now) {
        plan.validate(now);
        Set<UUID> colleagues = companies.recruitersOf(recruiter.companyId()).stream()
                .map(RecruiterSummary::userId).collect(Collectors.toSet());
        if (!colleagues.containsAll(plan.participantIds())) {
            throw new FieldValidationException("participantIds",
                    "les participants doivent être des recruteurs de votre entreprise");
        }
    }

    private static Map<String, Object> details(Interview interview, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("interviewId", interview.getId().toString());
        details.putAll(extra);
        return details;
    }
}
