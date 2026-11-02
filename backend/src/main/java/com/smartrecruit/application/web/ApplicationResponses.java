package com.smartrecruit.application.web;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.InterviewBrief;
import com.smartrecruit.application.application.ApplicationViews.CandidateApplication;
import com.smartrecruit.application.application.ApplicationViews.HistoryLine;
import com.smartrecruit.application.application.ApplicationViews.RecruiterDossier;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.api.CandidateStatus;
import com.smartrecruit.application.domain.CandidateTimeline;
import com.smartrecruit.application.domain.HistoryEventType;
import com.smartrecruit.application.domain.RejectionReason;
import com.smartrecruit.application.infrastructure.RecruiterApplicationRow;
import com.smartrecruit.candidate.api.CandidateDossier;
import com.smartrecruit.candidate.api.ResumeSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Réponses du module. Deux familles strictement séparées : la vue candidat ne partage aucun type
 * avec la vue recruteur, donc un champ interne ajouté côté recruteur ne peut pas fuiter côté
 * candidat par une sérialisation commune (RG-14, RG-23).
 * <p>
 * Conversion écrite à la main : les vues sont déjà assemblées par le service, il ne reste qu'à
 * renommer et aplatir quelques champs.
 */
final class ApplicationResponses {

    private ApplicationResponses() {
    }

    // ---- Vue candidat (docs/api.md §5) --------------------------------------------------------

    /**
     * @param timeline   absente de la liste ({@code null}, donc omise), présente dans le détail
     * @param interviews entretiens de la candidature, sans avis ni participants (RG-23)
     */
    record CandidateApplicationResponse(UUID id, CandidateJobRef job, CandidateStatus candidateStatus,
                                        Instant submittedAt, boolean canWithdraw, List<TimelineStep> timeline,
                                        List<InterviewRef> interviews) {
    }

    /** Entretien résumé : identique pour le candidat et le recruteur, car il ne contient rien d'interne. */
    record InterviewRef(UUID id, Instant startsAt, int durationMinutes, String type, String status) {
    }

    private static InterviewRef interview(InterviewBrief brief) {
        return new InterviewRef(brief.id(), brief.startsAt(), brief.durationMinutes(), brief.type(), brief.status());
    }

    record CandidateJobRef(UUID id, String title, String companyName) {
    }

    record TimelineStep(CandidateStatus candidateStatus, Instant at) {
    }

    static CandidateApplicationResponse candidateSummary(CandidateApplication view) {
        return new CandidateApplicationResponse(view.id(), jobRef(view), view.candidateStatus(), view.submittedAt(),
                view.canWithdraw(), null, null);
    }

    static CandidateApplicationResponse candidateDetail(CandidateApplication view) {
        return new CandidateApplicationResponse(view.id(), jobRef(view), view.candidateStatus(), view.submittedAt(),
                view.canWithdraw(), view.timeline().stream().map(ApplicationResponses::step).toList(),
                view.interviews().stream().map(ApplicationResponses::interview).toList());
    }

    private static CandidateJobRef jobRef(CandidateApplication view) {
        return new CandidateJobRef(view.jobId(), view.jobTitle(), view.companyName());
    }

    private static TimelineStep step(CandidateTimeline.Step step) {
        return new TimelineStep(step.candidateStatus(), step.at());
    }

    // ---- Vue recruteur (docs/api.md §7) -------------------------------------------------------

    record RecruiterApplicationItem(UUID id, CandidateRef candidate, JobRef job, ApplicationStatus status,
                                    Instant submittedAt, Integer lastMatchScore) {
    }

    record CandidateRef(UUID id, String fullName, String headline) {
    }

    record JobRef(UUID id, String title) {
    }

    static RecruiterApplicationItem item(RecruiterApplicationRow row) {
        return new RecruiterApplicationItem(row.id(),
                new CandidateRef(row.candidateId(), (row.firstName() + " " + row.lastName()).strip(), row.headline()),
                new JobRef(row.jobOfferId(), row.jobTitle()), row.status(), row.submittedAt(), row.lastMatchScore());
    }

    /** @param notesCount notes internes non supprimées */
    record DossierResponse(UUID id, ApplicationStatus status, List<ApplicationStatus> allowedTransitions,
                           Instant submittedAt, Instant statusChangedAt, JobRef job, CandidateProfile candidate,
                           ResumeRef resume, Rejection rejection, String withdrawalComment,
                           List<InterviewRef> interviews, long notesCount, Ai ai, long version) {
    }

    record CandidateProfile(UUID id, String fullName, String firstName, String lastName, String email, String phone,
                            String city, String countryCode, String headline, String summary,
                            Integer yearsOfExperience, List<CandidateDossier.ExperienceItem> experiences,
                            List<CandidateDossier.EducationItem> educations, List<CandidateDossier.SkillItem> skills,
                            List<CandidateDossier.LanguageItem> languages, List<CandidateDossier.LinkItem> links) {
    }

    record ResumeRef(UUID id, String originalFilename, int sizeBytes, Instant uploadedAt) {
    }

    record Rejection(RejectionReason reason, String comment) {
    }

    /** @param lastMatchedAt date du dernier matching (le détail : {@code GET /applications/{id}/ai/matching}) */
    record Ai(Integer lastMatchScore, Instant lastMatchedAt) {
    }

    static DossierResponse dossier(RecruiterDossier view) {
        Application application = view.application();
        CandidateDossier c = view.candidate();
        ResumeSummary resume = view.resume();
        return new DossierResponse(application.getId(), application.getStatus(),
                view.allowedTransitions().stream().sorted().toList(), application.getSubmittedAt(),
                application.getStatusChangedAt(), new JobRef(view.job().id(), view.job().title()),
                new CandidateProfile(c.id(), c.fullName(), c.firstName(), c.lastName(), c.email(), c.phone(), c.city(),
                        c.countryCode(), c.headline(), c.summary(), c.yearsOfExperience(), c.experiences(),
                        c.educations(), c.skills(), c.languages(), c.links()),
                resume == null ? null
                        : new ResumeRef(resume.id(), resume.originalFilename(), resume.sizeBytes(), resume.uploadedAt()),
                application.getRejectionReason() == null ? null
                        : new Rejection(application.getRejectionReason(), application.getRejectionComment()),
                application.getWithdrawalComment(),
                view.interviews().stream().map(ApplicationResponses::interview).toList(), view.notesCount(),
                new Ai(application.getLastMatchScore() == null ? null : application.getLastMatchScore().intValue(),
                        application.getLastMatchedAt()),
                application.getVersion() == null ? 0 : application.getVersion());
    }

    record HistoryResponse(UUID id, HistoryEventType type, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                           Actor actor, Map<String, Object> details, Instant occurredAt) {
    }

    record Actor(UUID id, String name) {
    }

    static HistoryResponse history(HistoryLine line) {
        var entry = line.entry();
        return new HistoryResponse(entry.id(), entry.type(), entry.fromStatus(), entry.toStatus(),
                entry.actorId() == null ? null : new Actor(entry.actorId(), line.actorName()), entry.details(),
                entry.occurredAt());
    }
}
