package com.smartrecruit.job.application;

import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobReference;
import com.smartrecruit.job.api.JobRequirements;
import com.smartrecruit.job.domain.JobSkill;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.infrastructure.JobOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implémentation de l'API du module pour les candidatures. */
@Service
@Transactional(readOnly = true)
class JobCatalogService implements JobCatalog {

    private final JobOfferRepository offers;
    private final Clock clock;

    JobCatalogService(JobOfferRepository offers, Clock clock) {
        this.offers = offers;
        this.clock = clock;
    }

    @Override
    public Optional<JobReference> find(UUID jobId) {
        return offers.findById(jobId).map(offer -> reference(offer, clock.instant()));
    }

    @Override
    public Map<UUID, JobReference> findAll(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        return offers.findAllById(jobIds).stream()
                .map(offer -> reference(offer, now))
                .collect(Collectors.toMap(JobReference::id, Function.identity()));
    }

    @Override
    public Optional<JobRequirements> requirements(UUID jobId) {
        return offers.findById(jobId).map(offer -> new JobRequirements(offer.getId(), offer.getTitle(),
                offer.getSkills().stream().filter(JobSkill::isRequired)
                        .map(skill -> new JobRequirements.RequiredSkill(skill.getSkillId(), skill.getMinLevel()))
                        .toList(),
                offer.getSkills().stream().filter(skill -> !skill.isRequired()).map(JobSkill::getSkillId).toList(),
                offer.getMinYearsExperience()));
    }

    private static JobReference reference(JobOffer offer, Instant now) {
        return new JobReference(offer.getId(), offer.getCompanyId(), offer.getTitle(), offer.getCity(),
                offer.isOpen(now));
    }
}
