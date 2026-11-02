package com.smartrecruit.job.application;

import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.job.api.JobApplicationStats;
import com.smartrecruit.job.application.JobViews.JobCard;
import com.smartrecruit.job.application.JobViews.JobDetail;
import com.smartrecruit.job.application.JobViews.RecruiterJob;
import com.smartrecruit.job.application.JobViews.RecruiterJobSummary;
import com.smartrecruit.job.application.JobViews.SkillRef;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobSkill;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.infrastructure.JobOfferRepository;
import com.smartrecruit.job.infrastructure.JobSearchCriteria;
import com.smartrecruit.job.infrastructure.JobSearchRepository;
import com.smartrecruit.job.infrastructure.JobSearchRow;
import com.smartrecruit.referential.api.CategoryCatalog;
import com.smartrecruit.referential.api.CategorySummary;
import com.smartrecruit.referential.api.SkillCatalog;
import com.smartrecruit.referential.api.SkillSummary;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.Role;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lectures des offres : espace recruteur (US-24) et recherche publique (US-25, US-26). */
@Service
@Transactional(readOnly = true)
public class JobQueryService {

    static final int TOP_SKILLS = 5;

    private final JobOfferRepository offers;
    private final JobSearchRepository search;
    private final CompanyDirectory companies;
    private final CategoryCatalog categories;
    private final SkillCatalog skills;
    private final UserAccounts accounts;
    private final ObjectProvider<JobApplicationStats> applicationStats;
    private final Clock clock;

    public JobQueryService(JobOfferRepository offers, JobSearchRepository search, CompanyDirectory companies,
                           CategoryCatalog categories, SkillCatalog skills, UserAccounts accounts,
                           ObjectProvider<JobApplicationStats> applicationStats, Clock clock) {
        this.offers = offers;
        this.search = search;
        this.companies = companies;
        this.categories = categories;
        this.skills = skills;
        this.accounts = accounts;
        this.applicationStats = applicationStats;
        this.clock = clock;
    }

    /** US-24. Tous statuts ; ARCHIVED masquées sauf si on les demande explicitement. */
    public PageResponse<RecruiterJobSummary> companyJobs(CurrentUser recruiter, JobStatus status, int page, int size) {
        UUID companyId = JobOfferService.companyOf(recruiter);
        PageRequest request = PageRequest.of(PageResponse.page(page), PageResponse.size(size),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<JobOffer> found = status == null
                ? offers.findByCompanyIdAndStatusNot(companyId, JobStatus.ARCHIVED, request)
                : offers.findByCompanyIdAndStatus(companyId, status, request);
        Map<UUID, Map<String, Long>> counts = counts(found.getContent().stream().map(JobOffer::getId).toList());
        Instant now = clock.instant();
        return PageResponse.of(found, offer -> new RecruiterJobSummary(offer.getId(), offer.getTitle(),
                offer.getStatus(), offer.getCity(), offer.getContractType(), offer.getPublishedAt(),
                offer.getExpiresAt(), offer.isExpired(now), counts.getOrDefault(offer.getId(), Map.of()),
                offer.getUpdatedAt()));
    }

    public RecruiterJob companyJob(CurrentUser recruiter, UUID id) {
        JobOffer offer = offers.findByIdAndCompanyId(id, JobOfferService.companyOf(recruiter))
                .orElseThrow(() -> new ResourceNotFoundException("Offre"));
        Map<UUID, SkillSummary> names = skills.findByIds(offer.getSkills().stream().map(JobSkill::getSkillId).toList());
        String createdBy = name(accounts.get(offer.getCreatedBy()));
        return new RecruiterJob(offer, skillRefs(offer, names, true), skillRefs(offer, names, false), createdBy,
                counts(List.of(id)).getOrDefault(id, Map.of()), offer.isExpired(clock.instant()));
    }

    /** US-25. Seules les offres publiées et non expirées (RG-11, RG-19). */
    public PageResponse<JobCard> search(JobSearchCriteria criteria) {
        PageResponse<JobSearchRow> rows = search.search(criteria, clock.instant());
        List<UUID> ids = rows.content().stream().map(JobSearchRow::id).toList();
        Map<UUID, CompanySummary> companyNames = companies.findAll(
                rows.content().stream().map(JobSearchRow::companyId).collect(Collectors.toSet()));
        Map<UUID, List<UUID>> topSkillIds = search.requiredSkills(ids, TOP_SKILLS);
        Map<UUID, SkillSummary> skillNames = skills.findByIds(
                topSkillIds.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
        List<JobCard> cards = rows.content().stream()
                .map(row -> new JobCard(row,
                        Optional.ofNullable(companyNames.get(row.companyId())).map(CompanySummary::name).orElse(""),
                        topSkillIds.getOrDefault(row.id(), List.of()).stream()
                                .map(skillId -> skillNames.containsKey(skillId) ? skillNames.get(skillId).name() : "?")
                                .toList()))
                .toList();
        return new PageResponse<>(cards, rows.page(), rows.size(), rows.totalElements(), rows.totalPages());
    }

    /** US-26. Une offre non publiée ou expirée n'existe pas pour le public (404). */
    public JobDetail publicJob(UUID id, Optional<CurrentUser> viewer) {
        JobOffer offer = offers.findById(id)
                .filter(found -> found.isOpen(clock.instant()))
                .orElseThrow(() -> new ResourceNotFoundException("Offre"));
        Map<UUID, SkillSummary> names = skills.findByIds(offer.getSkills().stream().map(JobSkill::getSkillId).toList());
        CompanySummary company = companies.find(offer.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Offre"));
        CategorySummary category = categories.findCategory(offer.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Offre"));
        Optional<CurrentUser> candidate = viewer.filter(user -> user.role() == Role.CANDIDATE);
        JobApplicationStats stats = applicationStats.getIfAvailable();
        UUID myApplicationId = candidate.flatMap(user -> stats == null
                ? Optional.<UUID>empty() : stats.applicationOf(id, user.id())).orElse(null);
        Boolean alreadyApplied = candidate.isPresent() ? myApplicationId != null : null;
        return new JobDetail(offer, company, category, skillRefs(offer, names, true), skillRefs(offer, names, false),
                alreadyApplied, myApplicationId);
    }

    private Map<UUID, Map<String, Long>> counts(List<UUID> jobIds) {
        JobApplicationStats stats = applicationStats.getIfAvailable();
        return stats == null || jobIds.isEmpty() ? Map.of() : stats.countsByStatus(jobIds);
    }

    private static List<SkillRef> skillRefs(JobOffer offer, Map<UUID, SkillSummary> names, boolean required) {
        return offer.getSkills().stream()
                .filter(skill -> skill.isRequired() == required)
                .map(skill -> new SkillRef(skill.getSkillId(),
                        names.containsKey(skill.getSkillId()) ? names.get(skill.getSkillId()).name() : "?",
                        skill.getMinLevel()))
                .toList();
    }

    private static String name(AccountSummary account) {
        return account.firstName() + " " + account.lastName();
    }
}
