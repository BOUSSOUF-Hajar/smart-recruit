package com.smartrecruit.job.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.job.api.JobApplicationStats;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.infrastructure.JobOfferRepository;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Écritures du recruteur sur les offres de son entreprise (US-21 à US-23). L'entreprise vient
 * toujours du token ({@link CurrentUser#companyId()}), jamais de la requête (RG-05) ; une offre
 * d'une autre entreprise répond 404, comme une offre inexistante.
 */
@Service
public class JobOfferService {

    static final String JOB_UPDATED = "JOB_UPDATED";

    private final JobOfferRepository offers;
    private final JobContentValidator validator;
    private final CompanyDirectory companies;
    private final ObjectProvider<JobApplicationStats> applicationStats;
    private final AuditRecorder audit;
    private final Clock clock;

    public JobOfferService(JobOfferRepository offers, JobContentValidator validator, CompanyDirectory companies,
                           ObjectProvider<JobApplicationStats> applicationStats, AuditRecorder audit, Clock clock) {
        this.offers = offers;
        this.validator = validator;
        this.companies = companies;
        this.applicationStats = applicationStats;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public JobOffer create(CurrentUser recruiter, JobDraft draft) {
        UUID companyId = companyOf(recruiter);
        return offers.save(JobOffer.draft(companyId, recruiter.id(), validator.validate(draft)));
    }

    /** US-22. Auditée si l'offre est déjà publiée (RG-17) : elle est visible des candidats. */
    @Transactional
    public JobOffer update(CurrentUser recruiter, UUID id, JobDraft draft, long version) {
        JobOffer offer = load(recruiter, id);
        if (offer.getVersion() == null || offer.getVersion() != version) {
            throw new OptimisticLockingFailureException("Offre " + id + " : version " + version + " obsolète");
        }
        offer.update(validator.validate(draft));
        if (offer.getStatus() == JobStatus.PUBLISHED) {
            audit.record(AuditEvent.success(JOB_UPDATED).by(recruiter.id(), null).on("JOB_OFFER", id));
        }
        offers.flush();   // la réponse porte la nouvelle version
        return offer;
    }

    /** RG-18 : brouillons seulement. */
    @Transactional
    public void delete(CurrentUser recruiter, UUID id) {
        JobOffer offer = load(recruiter, id);
        offer.checkDeletable();
        offers.delete(offer);
    }

    @Transactional
    public JobOffer publish(CurrentUser recruiter, UUID id, LocalDate expiryDate) {
        JobOffer offer = load(recruiter, id);
        offer.publish(expiryDate, company(offer).timezone(), clock.instant());
        return offer;
    }

    @Transactional
    public JobOffer unpublish(CurrentUser recruiter, UUID id) {
        JobOffer offer = load(recruiter, id);
        JobApplicationStats stats = applicationStats.getIfAvailable();
        offer.unpublish(stats != null && stats.hasApplications(id));
        return offer;
    }

    @Transactional
    public JobOffer close(CurrentUser recruiter, UUID id) {
        JobOffer offer = load(recruiter, id);
        offer.close();
        return offer;
    }

    @Transactional
    public JobOffer reopen(CurrentUser recruiter, UUID id, LocalDate expiryDate) {
        JobOffer offer = load(recruiter, id);
        offer.reopen(expiryDate, company(offer).timezone(), clock.instant());
        return offer;
    }

    @Transactional
    public JobOffer archive(CurrentUser recruiter, UUID id) {
        JobOffer offer = load(recruiter, id);
        offer.archive();
        return offer;
    }

    private JobOffer load(CurrentUser recruiter, UUID id) {
        return offers.findByIdAndCompanyId(id, companyOf(recruiter))
                .orElseThrow(() -> new ResourceNotFoundException("Offre"));
    }

    private CompanySummary company(JobOffer offer) {
        return companies.find(offer.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Entreprise"));
    }

    static UUID companyOf(CurrentUser recruiter) {
        if (recruiter.companyId() == null) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE,
                    "Votre compte recruteur n'est rattaché à aucune entreprise.");
        }
        return recruiter.companyId();
    }
}
