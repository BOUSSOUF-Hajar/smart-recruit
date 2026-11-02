package com.smartrecruit.application.application;

import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.candidate.api.ResumeUsage;
import com.smartrecruit.job.api.JobApplicationStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Points d'extension que ce module remplit pour les Offres (compteurs, US-23, US-24, US-26) et pour
 * les Candidats (CV joint, US-19). Ces modules déclarent l'interface ; celui-ci l'implémente, ce qui
 * garde les dépendances dans un seul sens.
 */
@Service
@Transactional(readOnly = true)
class ApplicationStatsService implements JobApplicationStats, ResumeUsage {

    private final ApplicationRepository applications;

    ApplicationStatsService(ApplicationRepository applications) {
        this.applications = applications;
    }

    @Override
    public Map<UUID, Map<String, Long>> countsByStatus(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Long>> counts = new HashMap<>();
        applications.countByStatus(jobIds).forEach(row -> counts
                .computeIfAbsent(row.getJobOfferId(), id -> new HashMap<>())
                .put(row.getStatus().name(), row.getTotal()));
        return counts;
    }

    @Override
    public boolean hasApplications(UUID jobId) {
        return applications.existsByJobOfferId(jobId);
    }

    @Override
    public Optional<UUID> applicationOf(UUID jobId, UUID candidateId) {
        return applications.findByJobOfferIdAndCandidateId(jobId, candidateId).map(Application::getId);
    }

    @Override
    public boolean isAttached(UUID resumeId) {
        return applications.existsByResumeId(resumeId);
    }
}
