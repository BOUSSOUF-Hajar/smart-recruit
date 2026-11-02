package com.smartrecruit.interview.domain;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.ForbiddenActionException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.persistence.VersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entretien d'une candidature (analyse fonctionnelle §4.3) :
 * <pre>
 * SCHEDULED → SCHEDULED (reprogrammer) → COMPLETED (après l'heure de début, avec avis) | CANCELLED
 * </pre>
 * COMPLETED et CANCELLED sont définitifs : toute action dessus donne {@code INVALID_STATE}.
 * <p>
 * L'entreprise et l'offre sont des copies immuables de la candidature (elles ne changent jamais) :
 * l'agenda de l'entreprise se lit sans joindre les tables du module Candidatures.
 */
@Entity
@Table(name = "interview")
public class Interview extends VersionedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "job_offer_id", nullable = false, updatable = false)
    private UUID jobOfferId;

    @Column(name = "scheduled_by", nullable = false, updatable = false)
    private UUID scheduledBy;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterviewType type;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;

    @Column(length = 255)
    private String address;

    @Column(name = "candidate_instructions", length = 1000)
    private String candidateInstructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterviewStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_rating", length = 8)
    private FeedbackRating feedbackRating;

    @Column(name = "feedback_comment", length = 2000)
    private String feedbackComment;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Chargés par lots pour une page d'agenda : une requête de plus par page, pas par entretien. */
    @ElementCollection
    @CollectionTable(name = "interview_participant", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "user_id", nullable = false)
    @BatchSize(size = 100)
    private Set<UUID> participantIds = new HashSet<>();

    protected Interview() {
        // JPA
    }

    /** F-INT-01. Le plan doit avoir été validé ({@link InterviewPlan#validate}). */
    public static Interview schedule(UUID applicationId, UUID companyId, UUID jobOfferId, UUID scheduledBy,
                                     InterviewPlan plan) {
        Interview interview = new Interview();
        interview.applicationId = Objects.requireNonNull(applicationId, "applicationId");
        interview.companyId = Objects.requireNonNull(companyId, "companyId");
        interview.jobOfferId = Objects.requireNonNull(jobOfferId, "jobOfferId");
        interview.scheduledBy = Objects.requireNonNull(scheduledBy, "scheduledBy");
        interview.status = InterviewStatus.SCHEDULED;
        interview.apply(plan);
        return interview;
    }

    /** F-INT-02 : en SCHEDULED uniquement. @return la date de début précédente */
    public Instant reschedule(InterviewPlan plan) {
        requireScheduled();
        Instant previous = startsAt;
        apply(plan);
        return previous;
    }

    /** F-INT-03 : annulation manuelle, ou automatique quand la candidature sort du processus (RG-22). */
    public void cancel(String reason) {
        requireScheduled();
        status = InterviewStatus.CANCELLED;
        cancellationReason = reason == null || reason.isBlank() ? null : reason.strip();
    }

    /** F-INT-04 : par un participant, une fois l'heure de début passée, avec un avis. */
    public void complete(UUID userId, FeedbackRating rating, String comment, Instant now) {
        requireScheduled();
        if (!participantIds.contains(userId)) {
            throw new ForbiddenActionException("Seul un participant peut clôturer cet entretien.");
        }
        if (now.isBefore(startsAt)) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE, "L'entretien n'a pas encore commencé.");
        }
        status = InterviewStatus.COMPLETED;
        feedbackRating = Objects.requireNonNull(rating, "rating");
        feedbackComment = comment == null || comment.isBlank() ? null : comment.strip();
        completedAt = now;
    }

    private void apply(InterviewPlan plan) {
        startsAt = plan.startsAt();
        durationMinutes = (short) plan.durationMinutes();
        type = plan.type();
        meetingUrl = plan.meetingUrl();
        address = plan.address();
        candidateInstructions = plan.candidateInstructions();
        participantIds.clear();
        participantIds.addAll(plan.participantIds());
    }

    /** Modifier, annuler ou clôturer : seulement en SCHEDULED ; sinon {@code INVALID_STATE}. */
    public void checkModifiable() {
        requireScheduled();
    }

    private void requireScheduled() {
        if (status != InterviewStatus.SCHEDULED) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE, status == InterviewStatus.COMPLETED
                    ? "Cet entretien est déjà clôturé." : "Cet entretien est annulé.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public UUID getScheduledBy() {
        return scheduledBy;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public InterviewType getType() {
        return type;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public String getAddress() {
        return address;
    }

    public String getCandidateInstructions() {
        return candidateInstructions;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public FeedbackRating getFeedbackRating() {
        return feedbackRating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Set<UUID> getParticipantIds() {
        return Set.copyOf(participantIds);
    }
}
