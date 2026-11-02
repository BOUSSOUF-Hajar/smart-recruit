package com.smartrecruit.application.web;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.application.ApplicationQueryService;
import com.smartrecruit.application.application.ApplicationService;
import com.smartrecruit.application.application.ChangeStatus;
import com.smartrecruit.application.infrastructure.ApplicationSort;
import com.smartrecruit.application.infrastructure.RecruiterApplicationCriteria;
import com.smartrecruit.application.web.ApplicationRequests.ChangeStatusRequest;
import com.smartrecruit.application.web.ApplicationResponses.DossierResponse;
import com.smartrecruit.application.web.ApplicationResponses.HistoryResponse;
import com.smartrecruit.application.web.ApplicationResponses.RecruiterApplicationItem;
import com.smartrecruit.candidate.api.ResumeContent;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Candidatures de l'entreprise du recruteur ({@code docs/api.md} §7). */
@RestController
@RequestMapping("/api/v1/applications")
class RecruiterApplicationController {

    private static final int MAX_NAME_LENGTH = 100;

    private final ApplicationService service;
    private final ApplicationQueryService queries;
    private final CompanyDirectory companies;

    RecruiterApplicationController(ApplicationService service, ApplicationQueryService queries,
                                   CompanyDirectory companies) {
        this.service = service;
        this.queries = queries;
        this.companies = companies;
    }

    /**
     * US-30. Listes séparées par des virgules ({@code status=SCREENING,SHORTLISTED}) ; les dates de la
     * période sont des jours calendaires dans le fuseau de l'entreprise, « au » inclus.
     */
    @GetMapping
    PageResponse<RecruiterApplicationItem> list(
            CurrentUser recruiter,
            @RequestParam(required = false) UUID jobOfferId,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) List<UUID> skillId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) String candidateName,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new FieldValidationException("minScore", "doit être compris entre 0 et 100");
        }
        if (submittedFrom != null && submittedTo != null && submittedTo.isBefore(submittedFrom)) {
            throw new FieldValidationException("submittedTo", "ne peut pas précéder la date de début");
        }
        ZoneId zone = zoneOf(recruiter);
        String name = candidateName != null && candidateName.length() > MAX_NAME_LENGTH
                ? candidateName.substring(0, MAX_NAME_LENGTH) : candidateName;
        RecruiterApplicationCriteria criteria = new RecruiterApplicationCriteria(recruiter.companyId(), jobOfferId,
                set(status), set(skillId), startOf(submittedFrom, zone),
                submittedTo == null ? null : startOf(submittedTo.plusDays(1), zone), name, minScore,
                ApplicationSort.parse(sort), PageResponse.page(page), PageResponse.size(size));
        var found = queries.recruiterApplications(criteria);
        return new PageResponse<>(found.content().stream().map(ApplicationResponses::item).toList(), found.page(),
                found.size(), found.totalElements(), found.totalPages());
    }

    /** US-31 */
    @GetMapping("/{id}")
    DossierResponse dossier(CurrentUser recruiter, @PathVariable UUID id) {
        return ApplicationResponses.dossier(queries.dossier(recruiter, id));
    }

    /** US-32, US-33. Réponse : le dossier relu, avec la nouvelle version et les transitions suivantes. */
    @PatchMapping("/{id}/status")
    DossierResponse changeStatus(CurrentUser recruiter, @PathVariable UUID id,
                                 @Valid @RequestBody ChangeStatusRequest request) {
        service.changeStatus(recruiter, id, new ChangeStatus(request.targetStatus(), request.rejectionReason(),
                request.comment(), request.version()));
        return ApplicationResponses.dossier(queries.dossier(recruiter, id));
    }

    /** US-34 */
    @GetMapping("/{id}/history")
    List<HistoryResponse> history(CurrentUser recruiter, @PathVariable UUID id) {
        return queries.history(recruiter, id).stream().map(ApplicationResponses::history).toList();
    }

    /** US-31 : CV figé à l'envoi, téléchargement forcé et audité, jamais mis en cache. */
    @GetMapping("/{id}/resume")
    ResponseEntity<Resource> resume(CurrentUser recruiter, @PathVariable UUID id) {
        ResumeContent content = queries.resume(recruiter, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(content.file());
    }

    private ZoneId zoneOf(CurrentUser recruiter) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Entreprise");
        }
        return companies.find(recruiter.companyId()).map(CompanySummary::timezone)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise"));
    }

    private static Instant startOf(LocalDate day, ZoneId zone) {
        return day == null ? null : day.atStartOfDay(zone).toInstant();
    }

    private static <T> Set<T> set(List<T> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
