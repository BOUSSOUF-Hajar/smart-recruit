package com.smartrecruit.application.domain;

import com.smartrecruit.shared.domain.ForbiddenActionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Note interne d'un recruteur sur une candidature (US-39). Visible de tous les recruteurs de
 * l'entreprise, modifiable et supprimable par son seul auteur ; jamais exposée au candidat (RG-23).
 * <p>
 * Dates gérées ici plutôt que par l'audit JPA : « modifiée » se lit {@code updatedAt > createdAt},
 * il faut donc que les deux soient strictement égaux à la création.
 */
@Entity
@Table(name = "note")
public class Note {

    public static final int MAX_LENGTH = 5000;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Note() {
        // JPA
    }

    public static Note write(UUID applicationId, UUID authorId, String content, Instant now) {
        Note note = new Note();
        note.applicationId = Objects.requireNonNull(applicationId, "applicationId");
        note.authorId = Objects.requireNonNull(authorId, "authorId");
        note.content = content.strip();
        note.createdAt = Objects.requireNonNull(now, "now");
        note.updatedAt = now;
        return note;
    }

    public void edit(UUID userId, String newContent, Instant now) {
        checkAuthor(userId);
        content = newContent.strip();
        updatedAt = now;
    }

    /** Suppression logique : la note disparaît de l'interface mais reste en base. */
    public void delete(UUID userId, Instant now) {
        checkAuthor(userId);
        deletedAt = now;
    }

    public boolean isEdited() {
        return updatedAt.isAfter(createdAt);
    }

    private void checkAuthor(UUID userId) {
        if (!authorId.equals(userId)) {
            throw new ForbiddenActionException("Seul l'auteur d'une note peut la modifier ou la supprimer.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
