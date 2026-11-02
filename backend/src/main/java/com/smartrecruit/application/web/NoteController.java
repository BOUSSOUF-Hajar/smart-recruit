package com.smartrecruit.application.web;

import com.smartrecruit.application.application.NoteService;
import com.smartrecruit.application.application.NoteService.NoteView;
import com.smartrecruit.application.domain.Note;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Notes internes d'une candidature ({@code docs/api.md} §7), réservées aux recruteurs de l'entreprise. */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/notes")
class NoteController {

    private final NoteService notes;

    NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    List<NoteResponse> list(CurrentUser recruiter, @PathVariable UUID applicationId) {
        return notes.list(recruiter, applicationId).stream().map(NoteController::response).toList();
    }

    @PostMapping
    ResponseEntity<NoteResponse> add(CurrentUser recruiter, @PathVariable UUID applicationId,
                                     @Valid @RequestBody NoteRequest request) {
        NoteView created = notes.add(recruiter, applicationId, request.content());
        return ResponseEntity.created(URI.create("/api/v1/applications/" + applicationId + "/notes/"
                + created.note().getId())).body(response(created));
    }

    @PutMapping("/{noteId}")
    NoteResponse edit(CurrentUser recruiter, @PathVariable UUID applicationId, @PathVariable UUID noteId,
                      @Valid @RequestBody NoteRequest request) {
        return response(notes.edit(recruiter, applicationId, noteId, request.content()));
    }

    @DeleteMapping("/{noteId}")
    ResponseEntity<Void> delete(CurrentUser recruiter, @PathVariable UUID applicationId, @PathVariable UUID noteId) {
        notes.delete(recruiter, applicationId, noteId);
        return ResponseEntity.noContent().build();
    }

    record NoteRequest(@NotBlank @Size(max = Note.MAX_LENGTH) String content) {
    }

    /** @param edited « modifiée » : affichée avec {@code updatedAt} */
    record NoteResponse(UUID id, String content, Author author, Instant createdAt, Instant updatedAt, boolean edited,
                        boolean mine) {
    }

    record Author(UUID id, String name) {
    }

    private static NoteResponse response(NoteView view) {
        Note note = view.note();
        return new NoteResponse(note.getId(), note.getContent(), new Author(note.getAuthorId(), view.authorName()),
                note.getCreatedAt(), note.getUpdatedAt(), note.isEdited(), view.mine());
    }
}
