package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationDirectory;
import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Implémentation de l'API du module pour les entretiens : trois requêtes, quel que soit le nombre demandé. */
@Service
@Transactional(readOnly = true)
class ApplicationDirectoryService implements ApplicationDirectory {

    private final ApplicationRepository applications;
    private final JobCatalog jobs;
    private final UserAccounts accounts;

    ApplicationDirectoryService(ApplicationRepository applications, JobCatalog jobs, UserAccounts accounts) {
        this.applications = applications;
        this.jobs = jobs;
        this.accounts = accounts;
    }

    @Override
    public Optional<ApplicationRef> find(UUID applicationId) {
        return Optional.ofNullable(findAll(List.of(applicationId)).get(applicationId));
    }

    @Override
    public Map<UUID, ApplicationRef> findAll(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        List<Application> found = applications.findAllById(applicationIds);
        Map<UUID, JobReference> jobRefs = jobs.findAll(
                found.stream().map(Application::getJobOfferId).collect(Collectors.toSet()));
        Map<UUID, AccountSummary> candidates = accounts.findAll(
                found.stream().map(Application::getCandidateId).collect(Collectors.toSet()));
        return found.stream()
                .filter(application -> jobRefs.containsKey(application.getJobOfferId()))
                .collect(Collectors.toMap(Application::getId, application -> {
                    JobReference job = jobRefs.get(application.getJobOfferId());
                    AccountSummary candidate = candidates.get(application.getCandidateId());
                    String name = candidate == null ? "" : (candidate.firstName() + " " + candidate.lastName()).strip();
                    return new ApplicationRef(application.getId(), application.getCandidateId(), name, job.id(),
                            job.title(), job.companyId(), application.getStatus(), application.getResumeId());
                }));
    }

    @Override
    public List<UUID> idsOfCandidate(UUID candidateId) {
        return applications.findIdsByCandidateId(candidateId);
    }
}
