package com.smartrecruit.reporting.web;

import com.smartrecruit.application.api.CandidateStatus;
import com.smartrecruit.reporting.application.DashboardService;
import com.smartrecruit.reporting.application.DashboardService.CandidateDashboard;
import com.smartrecruit.reporting.application.DashboardService.RecruiterDashboard;
import com.smartrecruit.reporting.infrastructure.DashboardQueries;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.CandidateInterview;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.JobRow;
import com.smartrecruit.reporting.infrastructure.DashboardQueries.UpcomingInterview;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Tableaux de bord ({@code docs/api.md} §5 et §10). */
@RestController
class DashboardController {

    private final DashboardService dashboards;

    DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    /** US-44 */
    @GetMapping("/api/v1/company/dashboard")
    RecruiterDashboardResponse recruiter(CurrentUser recruiter) {
        RecruiterDashboard d = dashboards.recruiter(recruiter);
        return new RecruiterDashboardResponse(d.jobsByStatus(),
                new Received(d.applications().last7Days(), d.applications().last30Days()),
                d.applications().inScreening(), d.applications().shortlisted(), d.upcomingInterviews(),
                new Hires(d.hiresLast30Days()), d.perJob());
    }

    /** US-46 */
    @GetMapping("/api/v1/admin/dashboard")
    AdminDashboardResponse admin() {
        DashboardService.AdminDashboard d = dashboards.admin();
        return new AdminDashboardResponse(d.accountsByRole(), d.companies(), d.jobsByStatus(), d.applications(),
                new AiState(d.ai().state().name(), d.ai().model()));
    }

    record AdminDashboardResponse(Map<String, DashboardQueries.AccountCounts> usersByRole,
                                  DashboardQueries.AccountCounts companies, Map<String, Long> jobsByStatus,
                                  DashboardQueries.PlatformApplications applications, AiState ai) {
    }

    record AiState(String status, String model) {
    }

    /** US-45 */
    @GetMapping("/api/v1/candidates/me/dashboard")
    CandidateDashboardResponse candidate(CurrentUser candidate) {
        CandidateDashboard d = dashboards.candidate(candidate);
        return new CandidateDashboardResponse(d.applicationsByStatus(), d.upcomingInterviews(),
                new Resume(d.profile().resumePresent(), d.profile().resumeUploadedAt()),
                new Completeness(d.profile().completenessPercent(), d.profile().missingCriteria()));
    }

    record RecruiterDashboardResponse(Map<String, Long> jobsByStatus, Received applicationsReceived, long inScreening,
                                      long shortlisted, List<UpcomingInterview> upcomingInterviews, Hires hires,
                                      List<JobRow> perJob) {
    }

    record Received(long last7Days, long last30Days) {
    }

    record Hires(long last30Days) {
    }

    record CandidateDashboardResponse(Map<CandidateStatus, Long> applicationsByStatus,
                                      List<CandidateInterview> upcomingInterviews, Resume resume,
                                      Completeness profileCompleteness) {
    }

    record Resume(boolean present, Instant uploadedAt) {
    }

    record Completeness(int percent, List<String> missing) {
    }
}
