package com.smartrecruit.application.application;

import com.smartrecruit.application.domain.HistoryEventType;
import com.smartrecruit.application.domain.Note;
import com.smartrecruit.application.infrastructure.HistoryRepository;
import com.smartrecruit.application.infrastructure.NoteRepository;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Notes internes (US-39). Le contrôle de propriété de la candidature passe par
 * {@link ApplicationAccess} (404 hors entreprise) ; celui de la note par {@link Note} (403 pour la
 * note d'un collègue, dont l'existence n'est pas un secret).
 */
@Service
public class NoteService {

    private final NoteRepository notes;
    private final ApplicationAccess access;
    private final HistoryRepository history;
    private final UserAccounts accounts;
    private final Clock clock;

    public NoteService(NoteRepository notes, ApplicationAccess access, HistoryRepository history,
                       UserAccounts accounts, Clock clock) {
        this.notes = notes;
        this.access = access;
        this.history = history;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Plus anciennes d'abord, comme une conversation ; auteurs en une requête. */
    @Transactional(readOnly = true)
    public List<NoteView> list(CurrentUser recruiter, UUID applicationId) {
        access.forRecruiter(recruiter, applicationId);
        List<Note> found = notes.findByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(applicationId);
        Map<UUID, AccountSummary> authors = accounts.findAll(
                found.stream().map(Note::getAuthorId).collect(Collectors.toSet()));
        return found.stream().map(note -> view(note, authors.get(note.getAuthorId()), recruiter)).toList();
    }

    @Transactional
    public NoteView add(CurrentUser recruiter, UUID applicationId, String content) {
        access.forRecruiter(recruiter, applicationId);
        Instant now = clock.instant();
        Note note = notes.saveAndFlush(Note.write(applicationId, recruiter.id(), content, now));
        // L'historique signale la note sans la recopier : son contenu peut encore changer ou disparaître
        history.append(applicationId, HistoryEventType.NOTE_ADDED, null, null, recruiter.id(),
                Map.of("noteId", note.getId().toString()), now);
        return view(note, accounts.get(recruiter.id()), recruiter);
    }

    @Transactional
    public NoteView edit(CurrentUser recruiter, UUID applicationId, UUID noteId, String content) {
        Note note = load(recruiter, applicationId, noteId);
        note.edit(recruiter.id(), content, clock.instant());
        notes.flush();
        return view(note, accounts.get(note.getAuthorId()), recruiter);
    }

    @Transactional
    public void delete(CurrentUser recruiter, UUID applicationId, UUID noteId) {
        load(recruiter, applicationId, noteId).delete(recruiter.id(), clock.instant());
    }

    @Transactional(readOnly = true)
    public long count(UUID applicationId) {
        return notes.countByApplicationIdAndDeletedAtIsNull(applicationId);
    }

    private Note load(CurrentUser recruiter, UUID applicationId, UUID noteId) {
        access.forRecruiter(recruiter, applicationId);
        return notes.findByIdAndApplicationIdAndDeletedAtIsNull(noteId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Note"));
    }

    private static NoteView view(Note note, AccountSummary author, CurrentUser viewer) {
        String name = author == null ? "" : (author.firstName() + " " + author.lastName()).strip();
        return new NoteView(note, name, note.getAuthorId().equals(viewer.id()));
    }

    /** @param mine l'appelant en est l'auteur : il peut la modifier ou la supprimer */
    public record NoteView(Note note, String authorName, boolean mine) {
    }
}
