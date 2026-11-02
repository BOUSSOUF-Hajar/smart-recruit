package com.smartrecruit.application.web;

import com.smartrecruit.application.application.ApplicationQueryService;
import com.smartrecruit.application.application.ApplicationService;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.web.ApplicationRequests.WithdrawRequest;
import com.smartrecruit.application.web.ApplicationResponses.CandidateApplicationResponse;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Candidatures du candidat connecté ({@code docs/api.md} §5). L'identité vient du token :
 * la candidature d'un autre candidat n'est pas atteignable (RG-06).
 */
@RestController
class CandidateApplicationController {

    private final ApplicationService service;
    private final ApplicationQueryService queries;

    CandidateApplicationController(ApplicationService service, ApplicationQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    /** US-27. Corps vide : le CV actif est figé, rien d'autre n'est saisi. */
    @PostMapping("/api/v1/jobs/{jobId}/applications")
    ResponseEntity<CandidateApplicationResponse> apply(CurrentUser candidate, @PathVariable UUID jobId) {
        Application created = service.apply(candidate, jobId);
        return ResponseEntity.created(URI.create("/api/v1/candidates/me/applications/" + created.getId()))
                .body(ApplicationResponses.candidateDetail(queries.candidateApplication(candidate, created.getId())));
    }

    /** US-28 : les plus récentes d'abord. */
    @GetMapping("/api/v1/candidates/me/applications")
    PageResponse<CandidateApplicationResponse> list(CurrentUser candidate,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        var found = queries.candidateApplications(candidate, page, size);
        return new PageResponse<>(found.content().stream().map(ApplicationResponses::candidateSummary).toList(),
                found.page(), found.size(), found.totalElements(), found.totalPages());
    }

    @GetMapping("/api/v1/candidates/me/applications/{id}")
    CandidateApplicationResponse detail(CurrentUser candidate, @PathVariable UUID id) {
        return ApplicationResponses.candidateDetail(queries.candidateApplication(candidate, id));
    }

    /** US-29. Corps facultatif {@code {"comment": "..."}}. */
    @PostMapping("/api/v1/candidates/me/applications/{id}/withdraw")
    CandidateApplicationResponse withdraw(CurrentUser candidate, @PathVariable UUID id,
                                          @Valid @RequestBody(required = false) WithdrawRequest request) {
        service.withdraw(candidate, id, request == null ? null : request.comment());
        return ApplicationResponses.candidateDetail(queries.candidateApplication(candidate, id));
    }
}
