package com.smartrecruit.reporting.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.CandidateStatus;
import com.smartrecruit.candidate.api.CandidateDirectory;
import com.smartrecruit.candidate.api.ProfileOverview;
import com.smartrecruit.reporting.infrastructure.DashboardQueries;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.ApplicationCounts;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.CandidateInterview;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.JobRow;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.UpcomingInterview;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import com.smartrecruit.ai.api.AiStatus;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tableaux de bord recruteur (US-44) et candidat (US-45), limités au périmètre de l'appelant. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int UPCOMING_LIMIT = 10;
    static final int JOBS_LIMIT = 50;

    private final DashboardQueries queries;
    private final CandidateDirectory candidates;
    private final ObjectProvider<AiStatus> ai;
    private final Clock clock;

    public DashboardService(DashboardQueries queries, CandidateDirectory candidates, ObjectProvider<AiStatus> ai,
                            Clock clock) {
        this.ai = ai;
        this.queries = queries;
        this.candidates = candidates;
        this.clock = clock;
    }

    /** Six requêtes, quel que soit le volume ; l'entreprise vient toujours du token (RG-05). */
    public RecruiterDashboard recruiter(CurrentUser recruiter) {
        UUID companyId = recruiter.companyId();
        if (companyId == null) {
            throw new ResourceNotFoundException("Entreprise");
        }
        Instant now = clock.instant();
        return new RecruiterDashboard(queries.jobsByStatus(companyId), queries.applicationCounts(companyId, now),
                queries.hiresSince(companyId, now.minus(Duration.ofDays(30))),
                queries.upcomingInterviews(companyId, now, UPCOMING_LIMIT), queries.perJob(companyId, JOBS_LIMIT));
    }

    /** Statuts traduits en libellés candidat par le serveur : SHORTLISTED n'apparaît jamais (D4.2). */
    public CandidateDashboard candidate(CurrentUser candidate) {
        Map<CandidateStatus, Long> byStatus = new EnumMap<>(CandidateStatus.class);
        queries.applicationsByInternalStatus(candidate.id()).forEach((status, count) ->
                byStatus.merge(CandidateStatus.of(ApplicationStatus.valueOf(status)), count, Long::sum));
        return new CandidateDashboard(byStatus, queries.candidateInterviews(candidate.id(), clock.instant(), 5),
                candidates.overview(candidate.id()));
    }

    /** US-46 : statistiques globales et état de l'IA ; aucune donnée de candidat ni de CV. */
    public AdminDashboard admin() {
        AiStatus status = ai.getIfAvailable();
        return new AdminDashboard(queries.accountsByRole(), queries.companies(), queries.allJobsByStatus(),
                queries.applications(clock.instant().minus(Duration.ofDays(30))),
                status == null ? new AiStatus.Snapshot(AiStatus.State.DISABLED, null) : status.current());
    }

    public record RecruiterDashboard(Map<String, Long> jobsByStatus, ApplicationCounts applications, long hiresLast30Days,
                                     List<UpcomingInterview> upcomingInterviews, List<JobRow> perJob) {
    }

    public record CandidateDashboard(Map<CandidateStatus, Long> applicationsByStatus,
                                     List<CandidateInterview> upcomingInterviews, ProfileOverview profile) {
    }

    public record AdminDashboard(Map<String, DashboardQueries.AccountCounts> accountsByRole,
                                 DashboardQueries.AccountCounts companies, Map<String, Long> jobsByStatus,
                                 DashboardQueries.PlatformApplications applications, AiStatus.Snapshot ai) {
    }
}
