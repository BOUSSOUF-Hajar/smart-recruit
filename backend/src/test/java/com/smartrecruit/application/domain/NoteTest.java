package com.smartrecruit.application.domain;

import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-39 : seul l'auteur modifie ou supprime ; « modifiée » après une modification. */
class NoteTest {

    private static final Instant T0 = Instant.parse("2026-10-22T09:00:00Z");

    @Test
    void anEditedNoteIsMarkedAndOnlyItsAuthorCanChangeIt() {
        UUID author = UUID.randomUUID();
        Note note = Note.write(UUID.randomUUID(), author, "  Bon niveau en Spring  ", T0);
        assertThat(note.getContent()).isEqualTo("Bon niveau en Spring");
        assertThat(note.isEdited()).isFalse();

        assertThatThrownBy(() -> note.edit(UUID.randomUUID(), "Autre avis", T0.plusSeconds(60)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> note.delete(UUID.randomUUID(), T0.plusSeconds(60)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);

        note.edit(author, "Très bon niveau en Spring", T0.plusSeconds(60));
        assertThat(note.isEdited()).isTrue();
        note.delete(author, T0.plusSeconds(120));
        assertThat(note.getDeletedAt()).isEqualTo(T0.plusSeconds(120));
    }
}
