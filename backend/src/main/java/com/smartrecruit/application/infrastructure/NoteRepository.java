package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID applicationId);

    long countByApplicationIdAndDeletedAtIsNull(UUID applicationId);

    /** La note doit appartenir à la candidature de l'URL : sinon 404, même pour une note existante. */
    Optional<Note> findByIdAndApplicationIdAndDeletedAtIsNull(UUID id, UUID applicationId);
}
