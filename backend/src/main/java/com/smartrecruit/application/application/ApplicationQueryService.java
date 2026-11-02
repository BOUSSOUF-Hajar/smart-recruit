package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationInterviews;
import com.smartrecruit.application.api.InterviewBrief;
import com.smartrecruit.application.application.ApplicationAccess.Owned;
import com.smartrecruit.application.application.ApplicationViews.CandidateApplication;
import com.smartrecruit.application.application.ApplicationViews.HistoryLine;
import com.smartrecruit.application.application.ApplicationViews.RecruiterDossier;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.domain.ApplicationWorkflow;
import com.smartrecruit.application.api.CandidateStatus;
import com.smartrecruit.application.domain.CandidateTimeline;
import com.smartrecruit.application.domain.HistoryEntry;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.application.infrastructure.HistoryRepository;
import com.smartrecruit.application.infrastructure.RecruiterApplicationCriteria;
import com.smartrecruit.application.infrastructure.RecruiterApplicationRow;
import com.smartrecruit.application.infrastructure.RecruiterApplicationSearch;
import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.candidate.api.CandidateDirectory;
import com.smartrecruit.candidate.api.ResumeContent;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobReference;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectures des candidatures : côté candidat (US-28) et côté recruteur (US-30, US-31, US-34).
 * Chaque liste se fait en un nombre fixe de requêtes, quelle que soit sa longueur.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationQueryService {

    static final String RESUME_DOWNLOADED = "RESUME_DOWNLOADED";

    private final ApplicationRepository applications;
    private final ApplicationAccess access;
    private final HistoryRepository history;
    private final RecruiterApplicationSearch search;
    private final JobCatalog jobs;
    private final CompanyDirectory companies;
    private final CandidateDirectory candidates;
    private final UserAccounts accounts;
    private final ApplicationService service;
    private final NoteService notes;
    private final ObjectProvider<ApplicationInterviews> interviews;
    private final AuditRecorder audit;

    public ApplicationQueryService(ApplicationRepository applications, ApplicationAccess access,
                                   HistoryRepository history, RecruiterApplicationSearch search, JobCatalog jobs,
                                   CompanyDirectory companies, CandidateDirectory candidates, UserAccounts accounts,
                                   ApplicationService service, NoteService notes,
                                   ObjectProvider<ApplicationInterviews> interviews, AuditRecorder audit) {
        this.applications = applications;
        this.access = access;
        this.history = history;
        this.search = search;
        this.jobs = jobs;
        this.companies = companies;
        this.candidates = candidates;
        this.accounts = accounts;
        this.service = service;
        this.notes = notes;
        this.interviews = interviews;
        this.audit = audit;
    }

    /** US-28 : trois requêtes par page (candidatures, offres, entreprises). */
    public PageResponse<CandidateApplication> candidateApplications(CurrentUser candidate, int page, int size) {
        Page<Application> found = applications.findByCandidateId(candidate.id(), PageRequest.of(
                PageResponse.page(page), PageResponse.size(size),
                Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("id"))));
        Map<UUID, JobReference> jobRefs = jobs.findAll(
                found.getContent().stream().map(Application::getJobOfferId).collect(Collectors.toSet()));
        Map<UUID, CompanySummary> companyRefs = companies.findAll(
                jobRefs.values().stream().map(JobReference::companyId).collect(Collectors.toSet()));
        return PageResponse.of(found, application -> candidateView(application,
                jobRefs.get(application.getJobOfferId()), companyRefs, List.of(), List.of()));
    }

    /** US-28, US-34 : détail avec la chronologie des statuts simplifiés. */
    public CandidateApplication candidateApplication(CurrentUser candidate, UUID applicationId) {
        Application application = access.forCandidate(candidate, applicationId);
        JobReference job = jobs.find(application.getJobOfferId()).orElse(null);
        Map<UUID, CompanySummary> companyRefs = job == null ? Map.of() : companies.findAll(Set.of(job.companyId()));
        return candidateView(application, job, companyRefs, CandidateTimeline.of(history.findByApplication(applicationId)),
                interviewsOf(applicationId));
    }

    /** US-30 : l'entreprise vient toujours du token (RG-05). */
    public PageResponse<RecruiterApplicationRow> recruiterApplications(RecruiterApplicationCriteria criteria) {
        return search.search(criteria);
    }

    /** US-31 : profil du candidat, CV figé, transitions proposées. */
    public RecruiterDossier dossier(CurrentUser recruiter, UUID applicationId) {
        Owned owned = access.forRecruiter(recruiter, applicationId);
        Application application = owned.application();
        return new RecruiterDossier(application, owned.job(), candidates.dossier(application.getCandidateId()),
                candidates.resume(application.getResumeId()).orElse(null),
                ApplicationWorkflow.recruiterChoices(application.getStatus(),
                        service.hasCompletedInterview(applicationId)),
                interviewsOf(applicationId), notes.count(applicationId));
    }

    /** US-34 : plus récent d'abord, avec le nom de chaque auteur (une requête pour tous). */
    public List<HistoryLine> history(CurrentUser recruiter, UUID applicationId) {
        access.forRecruiter(recruiter, applicationId);
        List<HistoryEntry> entries = history.findByApplication(applicationId);
        Map<UUID, AccountSummary> actors = accounts.findAll(
                entries.stream().map(HistoryEntry::actorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return entries.stream()
                .map(entry -> new HistoryLine(entry, entry.actorId() == null || !actors.containsKey(entry.actorId())
                        ? null : name(actors.get(entry.actorId()))))
                .toList();
    }

    /**
     * US-31 : la version du CV envoyée avec la candidature (RG-12), même si le candidat en a changé
     * depuis. Transaction en écriture : l'audit du téléchargement y est inséré.
     */
    @Transactional
    public ResumeContent resume(CurrentUser recruiter, UUID applicationId) {
        Application application = access.forRecruiter(recruiter, applicationId).application();
        ResumeContent content = candidates.resumeContent(application.getResumeId());
        audit.record(AuditEvent.success(RESUME_DOWNLOADED).by(recruiter.id(), null)
                .on("RESUME", application.getResumeId()).with("applicationId", applicationId));
        return content;
    }

    private static CandidateApplication candidateView(Application application, JobReference job,
                                                      Map<UUID, CompanySummary> companyRefs,
                                                      List<CandidateTimeline.Step> timeline,
                                                      List<InterviewBrief> interviewBriefs) {
        if (job == null) {
            throw new ResourceNotFoundException("Offre");   // impossible : clé étrangère RESTRICT
        }
        CompanySummary company = companyRefs.get(job.companyId());
        return new CandidateApplication(application.getId(), job.id(), job.title(),
                company == null ? "" : company.name(), CandidateStatus.of(application.getStatus()),
                application.getSubmittedAt(), ApplicationWorkflow.candidateCanWithdraw(application.getStatus()),
                timeline, interviewBriefs);
    }

    private List<InterviewBrief> interviewsOf(UUID applicationId) {
        ApplicationInterviews source = interviews.getIfAvailable();
        return source == null ? List.of() : source.of(applicationId);
    }

    private static String name(AccountSummary account) {
        return (account.firstName() + " " + account.lastName()).strip();
    }
}
