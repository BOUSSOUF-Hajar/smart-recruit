package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.InterviewBrief;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.api.CandidateStatus;
import com.smartrecruit.application.domain.CandidateTimeline;
import com.smartrecruit.application.domain.HistoryEntry;
import com.smartrecruit.candidate.api.CandidateDossier;
import com.smartrecruit.candidate.api.ResumeSummary;
import com.smartrecruit.job.api.JobReference;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Vues assemblées par {@link ApplicationQueryService}, prêtes à être converties en réponses. */
public final class ApplicationViews {

    private ApplicationViews() {
    }

    /**
     * Candidature vue par son auteur (US-28). Ne contient par construction aucun statut interne,
     * motif, commentaire de rejet ni note (RG-14, RG-23).
     *
     * @param timeline   vide dans la liste, renseignée dans le détail
     * @param interviews idem ; résumés sans lieu ni avis (RG-23)
     */
    public record CandidateApplication(UUID id, UUID jobId, String jobTitle, String companyName,
                                       CandidateStatus candidateStatus, Instant submittedAt, boolean canWithdraw,
                                       List<CandidateTimeline.Step> timeline, List<InterviewBrief> interviews) {
    }

    /** Dossier vu par un recruteur de l'entreprise (US-31). */
    public record RecruiterDossier(Application application, JobReference job, CandidateDossier candidate,
                                   ResumeSummary resume, Set<ApplicationStatus> allowedTransitions,
                                   List<InterviewBrief> interviews, long notesCount) {
    }

    /** Entrée d'historique avec le nom de son auteur ({@code null} pour le système). */
    public record HistoryLine(HistoryEntry entry, String actorName) {
    }
}
