package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.shared.persistence.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Candidature d'un candidat à une offre (US-27). Le statut ne change que par {@link #moveTo}, qui
 * applique les règles d'{@link ApplicationWorkflow} : jamais de modification libre du champ.
 * <p>
 * Les autres modules sont référencés par identifiant (offre, candidat, CV) : aucune relation JPA
 * vers leurs entités, qui ne sont pas visibles d'ici.
 */
@Entity
@Table(name = "application")
public class Application extends VersionedEntity {

    public static final int MAX_COMMENT = 1000;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "job_offer_id", nullable = false, updatable = false)
    private UUID jobOfferId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    /** RG-12 : version du CV figée à l'envoi ; un nouveau CV ne change rien aux candidatures envoyées. */
    @Column(name = "resume_id", nullable = false, updatable = false)
    private UUID resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 32)
    private RejectionReason rejectionReason;

    @Column(name = "rejection_comment", length = MAX_COMMENT)
    private String rejectionComment;

    @Column(name = "withdrawal_comment", length = MAX_COMMENT)
    private String withdrawalComment;

    /**
     * Copies du dernier matching, écrites seulement par {@code ApplicationRepository.recordMatchScore}
     * (mise à jour ciblée) : un résultat IA arrivé pendant que le recruteur lit le dossier ne rend pas
     * sa version périmée. Jamais modifiées par l'entité, d'où {@code updatable = false}.
     */
    @Column(name = "last_match_score", insertable = false, updatable = false)
    private Short lastMatchScore;

    @Column(name = "last_matched_at", insertable = false, updatable = false)
    private Instant lastMatchedAt;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected Application() {
        // JPA
    }

    public static Application submit(UUID jobOfferId, UUID candidateId, UUID resumeId, Instant now) {
        Application application = new Application();
        application.jobOfferId = Objects.requireNonNull(jobOfferId, "jobOfferId");
        application.candidateId = Objects.requireNonNull(candidateId, "candidateId");
        application.resumeId = Objects.requireNonNull(resumeId, "resumeId");
        application.status = ApplicationStatus.SUBMITTED;
        application.submittedAt = Objects.requireNonNull(now, "now");
        application.statusChangedAt = now;
        return application;
    }

    /**
     * Applique une transition (US-29, US-32, US-33).
     *
     * @param comment commentaire interne du rejet, ou commentaire du candidat qui se retire ; ignoré sinon
     */
    public StatusChange moveTo(ApplicationStatus target, TransitionContext context, String comment, Instant now) {
        ApplicationWorkflow.check(status, target, context);
        ApplicationStatus from = status;
        status = target;
        statusChangedAt = now;
        if (target == ApplicationStatus.REJECTED) {
            rejectionReason = context.rejectionReason();
            rejectionComment = blankToNull(comment);
        } else if (target == ApplicationStatus.WITHDRAWN) {
            withdrawalComment = blankToNull(comment);
        }
        return new StatusChange(from, target);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public String getRejectionComment() {
        return rejectionComment;
    }

    public String getWithdrawalComment() {
        return withdrawalComment;
    }

    public Short getLastMatchScore() {
        return lastMatchScore;
    }

    public Instant getLastMatchedAt() {
        return lastMatchedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
