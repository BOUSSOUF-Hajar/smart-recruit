package com.smartrecruit.job.web;

import com.smartrecruit.job.application.JobOfferService;
import com.smartrecruit.job.application.JobQueryService;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.web.JobRequests.ExpiryRequest;
import com.smartrecruit.job.web.JobRequests.JobRequest;
import com.smartrecruit.job.web.JobResponses.RecruiterJobResponse;
import com.smartrecruit.job.web.JobResponses.RecruiterJobSummaryResponse;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Offres de l'entreprise du recruteur ({@code docs/api.md} §6). Chaque écriture est suivie d'une
 * relecture : la réponse est exactement ce que renverrait {@code GET /company/jobs/{id}}.
 */
@RestController
class RecruiterJobController {

    private final JobOfferService service;
    private final JobQueryService queries;
    private final JobWebMapper mapper;

    RecruiterJobController(JobOfferService service, JobQueryService queries, JobWebMapper mapper) {
        this.service = service;
        this.queries = queries;
        this.mapper = mapper;
    }

    /** US-24 */
    @GetMapping("/api/v1/company/jobs")
    PageResponse<RecruiterJobSummaryResponse> list(CurrentUser recruiter,
                                                   @RequestParam(required = false) JobStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        var jobs = queries.companyJobs(recruiter, status, page, size);
        return new PageResponse<>(jobs.content().stream().map(mapper::toResponse).toList(), jobs.page(), jobs.size(),
                jobs.totalElements(), jobs.totalPages());
    }

    @GetMapping("/api/v1/company/jobs/{id}")
    RecruiterJobResponse get(CurrentUser recruiter, @PathVariable UUID id) {
        return view(recruiter, id);
    }

    /** US-21 */
    @PostMapping("/api/v1/jobs")
    ResponseEntity<RecruiterJobResponse> create(CurrentUser recruiter, @Valid @RequestBody JobRequest request) {
        JobOffer created = service.create(recruiter, request.toDraft());
        return ResponseEntity.created(URI.create("/api/v1/company/jobs/" + created.getId()))
                .body(view(recruiter, created.getId()));
    }

    /** US-22 */
    @PutMapping("/api/v1/jobs/{id}")
    RecruiterJobResponse update(CurrentUser recruiter, @PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        if (request.version() == null) {
            throw new FieldValidationException("version", "Obligatoire pour modifier une offre.");
        }
        service.update(recruiter, id, request.toDraft(), request.version());
        return view(recruiter, id);
    }

    @DeleteMapping("/api/v1/jobs/{id}")
    ResponseEntity<Void> delete(CurrentUser recruiter, @PathVariable UUID id) {
        service.delete(recruiter, id);
        return ResponseEntity.noContent().build();
    }

    /** US-23 : les cinq actions du cycle de vie. */
    @PostMapping("/api/v1/jobs/{id}/publish")
    RecruiterJobResponse publish(CurrentUser recruiter, @PathVariable UUID id,
                                 @Valid @RequestBody ExpiryRequest request) {
        service.publish(recruiter, id, request.expiryDate());
        return view(recruiter, id);
    }

    @PostMapping("/api/v1/jobs/{id}/unpublish")
    RecruiterJobResponse unpublish(CurrentUser recruiter, @PathVariable UUID id) {
        service.unpublish(recruiter, id);
        return view(recruiter, id);
    }

    @PostMapping("/api/v1/jobs/{id}/close")
    RecruiterJobResponse close(CurrentUser recruiter, @PathVariable UUID id) {
        service.close(recruiter, id);
        return view(recruiter, id);
    }

    @PostMapping("/api/v1/jobs/{id}/reopen")
    RecruiterJobResponse reopen(CurrentUser recruiter, @PathVariable UUID id,
                                @Valid @RequestBody ExpiryRequest request) {
        service.reopen(recruiter, id, request.expiryDate());
        return view(recruiter, id);
    }

    @PostMapping("/api/v1/jobs/{id}/archive")
    RecruiterJobResponse archive(CurrentUser recruiter, @PathVariable UUID id) {
        service.archive(recruiter, id);
        return view(recruiter, id);
    }

    private RecruiterJobResponse view(CurrentUser recruiter, UUID id) {
        return mapper.toResponse(queries.companyJob(recruiter, id));
    }
}
