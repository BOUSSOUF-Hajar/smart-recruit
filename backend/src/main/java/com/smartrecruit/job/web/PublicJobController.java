package com.smartrecruit.job.web;

import com.smartrecruit.job.application.JobQueryService;
import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.WorkMode;
import com.smartrecruit.job.infrastructure.JobSearchCriteria;
import com.smartrecruit.job.infrastructure.JobSort;
import com.smartrecruit.job.web.JobResponses.JobCardResponse;
import com.smartrecruit.job.web.JobResponses.JobDetailResponse;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Recherche et détail des offres, publics ({@code docs/api.md} §3). */
@RestController
@RequestMapping("/api/v1/jobs")
class PublicJobController {

    private static final int MAX_QUERY_LENGTH = 200;

    private final JobQueryService queries;
    private final JobWebMapper mapper;

    PublicJobController(JobQueryService queries, JobWebMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    /** US-25. Les listes acceptent des valeurs séparées par des virgules : {@code workMode=REMOTE,HYBRID}. */
    @GetMapping
    PageResponse<JobCardResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) List<WorkMode> workMode,
            @RequestParam(required = false) List<ContractType> contractType,
            @RequestParam(required = false) List<ExperienceLevel> experienceLevel,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<UUID> skillId,
            @RequestParam(required = false) BigDecimal salaryMin,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String query = q != null && q.length() > MAX_QUERY_LENGTH ? q.substring(0, MAX_QUERY_LENGTH) : q;
        JobSearchCriteria criteria = new JobSearchCriteria(query, city, countryCode, set(workMode), set(contractType),
                set(experienceLevel), categoryId, set(skillId), salaryMin,
                JobSort.parse(sort).orElse(JobSort.DEFAULT),   // tri inconnu : ignoré (liste blanche)
                PageResponse.page(page), PageResponse.size(size));
        var cards = queries.search(criteria);
        return new PageResponse<>(cards.content().stream().map(mapper::toResponse).toList(), cards.page(),
                cards.size(), cards.totalElements(), cards.totalPages());
    }

    /** US-26. Le token est facultatif : s'il est présent, un candidat apprend s'il a déjà postulé. */
    @GetMapping("/{id}")
    JobDetailResponse detail(@PathVariable UUID id, Optional<CurrentUser> viewer) {
        return mapper.toResponse(queries.publicJob(id, viewer));
    }

    private static <T> Set<T> set(List<T> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
