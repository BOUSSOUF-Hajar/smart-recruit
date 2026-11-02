package com.smartrecruit.interview.web;

import com.smartrecruit.interview.application.InterviewQueryService;
import com.smartrecruit.interview.application.InterviewService;
import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.domain.InterviewStatus;
import com.smartrecruit.interview.infrastructure.InterviewCriteria;
import com.smartrecruit.interview.web.InterviewRequests.CancelRequest;
import com.smartrecruit.interview.web.InterviewRequests.CompleteRequest;
import com.smartrecruit.interview.web.InterviewRequests.InterviewRequest;
import com.smartrecruit.interview.web.InterviewResponses.RecruiterInterviewResponse;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Entretiens côté recruteur ({@code docs/api.md} §8) : la création part de la candidature. */
@RestController
class RecruiterInterviewController {

    private final InterviewService service;
    private final InterviewQueryService queries;

    RecruiterInterviewController(InterviewService service, InterviewQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    /** US-35 */
    @PostMapping("/api/v1/applications/{applicationId}/interviews")
    ResponseEntity<RecruiterInterviewResponse> schedule(CurrentUser recruiter, @PathVariable UUID applicationId,
                                                        @Valid @RequestBody InterviewRequest request) {
        Interview created = service.schedule(recruiter, applicationId, request.toPlan());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + created.getId()))
                .body(InterviewResponses.recruiter(queries.reread(created.getId())));
    }

    /** F-INT-05 : agenda de l'entreprise ; {@code mine=true} pour ceux où l'on participe. */
    @GetMapping("/api/v1/interviews")
    PageResponse<RecruiterInterviewResponse> agenda(
            CurrentUser recruiter,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) List<InterviewStatus> status,
            @RequestParam(required = false) UUID jobOfferId,
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Entreprise");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw new FieldValidationException("to", "doit être postérieur au début de la période");
        }
        var found = queries.agenda(new InterviewCriteria(recruiter.companyId(), from, to,
                status == null ? null : new HashSet<>(status), jobOfferId, applicationId,
                mine ? recruiter.id() : null), page, size);
        return new PageResponse<>(found.content().stream().map(InterviewResponses::recruiter).toList(),
                found.page(), found.size(), found.totalElements(), found.totalPages());
    }

    @GetMapping("/api/v1/interviews/{id}")
    RecruiterInterviewResponse detail(CurrentUser recruiter, @PathVariable UUID id) {
        return InterviewResponses.recruiter(queries.detail(recruiter, id));
    }

    /** US-36 : reprogrammer ou modifier ; {@code version} obligatoire. */
    @PutMapping("/api/v1/interviews/{id}")
    RecruiterInterviewResponse reschedule(CurrentUser recruiter, @PathVariable UUID id,
                                          @Valid @RequestBody InterviewRequest request) {
        if (request.version() == null) {
            throw new FieldValidationException("version", "obligatoire pour modifier un entretien");
        }
        service.reschedule(recruiter, id, request.toPlan(), request.version());
        return InterviewResponses.recruiter(queries.reread(id));
    }

    /** US-36. Corps facultatif {@code {"reason": "..."}}. */
    @PostMapping("/api/v1/interviews/{id}/cancel")
    RecruiterInterviewResponse cancel(CurrentUser recruiter, @PathVariable UUID id,
                                      @Valid @RequestBody(required = false) CancelRequest request) {
        service.cancel(recruiter, id, request == null ? null : request.reason());
        return InterviewResponses.recruiter(queries.reread(id));
    }

    /** US-37 */
    @PostMapping("/api/v1/interviews/{id}/complete")
    RecruiterInterviewResponse complete(CurrentUser recruiter, @PathVariable UUID id,
                                        @Valid @RequestBody CompleteRequest request) {
        service.complete(recruiter, id, request.rating(), request.comment());
        return InterviewResponses.recruiter(queries.reread(id));
    }
}
