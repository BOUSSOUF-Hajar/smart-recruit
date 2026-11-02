package com.smartrecruit.ai.domain;

import com.smartrecruit.shared.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tâche IA asynchrone (analyse fonctionnelle §4.4). Relancer crée une nouvelle tâche : une tâche
 * terminée n'est jamais réutilisée. Le verrou optimiste empêche deux exécuteurs de la prendre.
 */
@Entity
@Table(name = "ai_task")
public class AiTask {

    /** Seules erreurs possibles d'une tâche ; la base les répète en contrainte CHECK. */
    public static final Set<ErrorCode> ERROR_CODES =
            EnumSet.of(ErrorCode.AI_UNAVAILABLE, ErrorCode.AI_INVALID_RESPONSE, ErrorCode.FILE_UNREADABLE);

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AiTaskType type;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "resume_id", nullable = false, updatable = false)
    private UUID resumeId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 32)
    private ErrorCode errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected AiTask() {
        // JPA
    }

    public static AiTask request(AiTaskType type, UUID applicationId, UUID companyId, UUID resumeId,
                                 UUID requestedBy, Instant now) {
        AiTask task = new AiTask();
        task.type = Objects.requireNonNull(type, "type");
        task.applicationId = Objects.requireNonNull(applicationId, "applicationId");
        task.companyId = Objects.requireNonNull(companyId, "companyId");
        task.resumeId = Objects.requireNonNull(resumeId, "resumeId");
        task.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        task.status = AiTaskStatus.PENDING;
        task.createdAt = Objects.requireNonNull(now, "now");
        return task;
    }

    /** @return faux si la tâche n'est plus PENDING (déjà prise, ou passée FAILED au redémarrage) */
    public boolean start(Instant now) {
        if (status != AiTaskStatus.PENDING) {
            return false;
        }
        status = AiTaskStatus.RUNNING;
        startedAt = now;
        return true;
    }

    public void succeed(Instant now) {
        if (status != AiTaskStatus.RUNNING) {
            throw new IllegalStateException("Tâche " + id + " : DONE impossible depuis " + status);
        }
        status = AiTaskStatus.DONE;
        finishedAt = now;
    }

    /** Depuis PENDING (orpheline, file pleine) ou RUNNING ; sans effet sur une tâche déjà terminée. */
    public void fail(ErrorCode code, Instant now) {
        if (!ERROR_CODES.contains(code)) {
            throw new IllegalArgumentException("Code d'erreur IA inattendu : " + code);
        }
        if (status == AiTaskStatus.PENDING || status == AiTaskStatus.RUNNING) {
            status = AiTaskStatus.FAILED;
            errorCode = code;
            finishedAt = now;
        }
    }

    public boolean isActive() {
        return status == AiTaskStatus.PENDING || status == AiTaskStatus.RUNNING;
    }

    public UUID getId() {
        return id;
    }

    public AiTaskType getType() {
        return type;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public AiTaskStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
