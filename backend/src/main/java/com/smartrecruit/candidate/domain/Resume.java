package com.smartrecruit.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une version du CV d'un candidat (US-17). Au plus une active (RG-08, index unique partiel) ;
 * les anciennes restent en base, car une candidature déjà envoyée garde la sienne (RG-12).
 * <p>
 * Le fichier est dans le stockage sous {@code storageKey} ; le nom d'origine n'est qu'une métadonnée.
 */
@Entity
@Table(name = "resume")
public class Resume {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 100)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    protected Resume() {
        // JPA
    }

    public static Resume activeVersion(UUID candidateId, String storageKey, String originalFilename,
                                       int sizeBytes, String sha256, Instant now) {
        Resume resume = new Resume();
        resume.candidateId = Objects.requireNonNull(candidateId, "candidateId");
        resume.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        resume.originalFilename = Objects.requireNonNull(originalFilename, "originalFilename");
        resume.contentType = ResumeRules.CONTENT_TYPE;
        resume.sizeBytes = sizeBytes;
        resume.sha256 = Objects.requireNonNull(sha256, "sha256");
        resume.active = true;
        resume.uploadedAt = Objects.requireNonNull(now, "now");
        return resume;
    }

    /** Remplacé par une nouvelle version : n'est plus proposé pour les candidatures futures. */
    public void deactivate(Instant now) {
        if (active) {
            active = false;
            deactivatedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }
}
