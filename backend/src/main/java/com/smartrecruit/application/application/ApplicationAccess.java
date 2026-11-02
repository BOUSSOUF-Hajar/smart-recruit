package com.smartrecruit.application.application;

import com.smartrecruit.application.domain.Application;
import com.smartrecruit.application.infrastructure.ApplicationRepository;
import com.smartrecruit.job.api.JobCatalog;
import com.smartrecruit.job.api.JobReference;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Contrôle de propriété des candidatures (RG-05, RG-06), au même endroit pour les écritures et les
 * lectures. Hors périmètre → 404, jamais 403 : on ne confirme pas l'existence de la ressource.
 */
@Component
class ApplicationAccess {

    private final ApplicationRepository applications;
    private final JobCatalog jobs;

    ApplicationAccess(ApplicationRepository applications, JobCatalog jobs) {
        this.applications = applications;
        this.jobs = jobs;
    }

    /** Candidature d'une offre de l'entreprise du recruteur, avec cette offre. */
    Owned forRecruiter(CurrentUser recruiter, UUID applicationId) {
        Application application = applications.findById(applicationId).orElseThrow(ApplicationAccess::notFound);
        JobReference job = jobs.find(application.getJobOfferId())
                .filter(found -> recruiter.isRecruiterOf(found.companyId()))
                .orElseThrow(ApplicationAccess::notFound);
        return new Owned(application, job);
    }

    /** Candidature du candidat connecté. */
    Application forCandidate(CurrentUser candidate, UUID applicationId) {
        return applications.findByIdAndCandidateId(applicationId, candidate.id())
                .orElseThrow(ApplicationAccess::notFound);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Candidature");
    }

    record Owned(Application application, JobReference job) {
    }
}
