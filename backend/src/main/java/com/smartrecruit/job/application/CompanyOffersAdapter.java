package com.smartrecruit.job.application;

import com.smartrecruit.company.api.CompanyOffers;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.infrastructure.JobOfferRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offres vues par l'administration des entreprises (US-49, RG-31). */
@Component
class CompanyOffersAdapter implements CompanyOffers {

    private final JobOfferRepository offers;

    CompanyOffersAdapter(JobOfferRepository offers) {
        this.offers = offers;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, OfferCounts> countsOf(Collection<UUID> companyIds) {
        Map<UUID, OfferCounts> counts = new HashMap<>();
        if (companyIds.isEmpty()) {
            return counts;
        }
        offers.countByCompany(companyIds).forEach(row ->
                counts.put(row.getCompanyId(), new OfferCounts(row.getTotal(), row.getPublished())));
        return counts;
    }

    /** Par les entités : la règle « seule une offre publiée se clôture » reste celle du domaine. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int closePublished(UUID companyId) {
        List<JobOffer> published = offers.findByCompanyIdAndStatus(companyId, JobStatus.PUBLISHED);
        published.forEach(JobOffer::close);
        return published.size();
    }
}
