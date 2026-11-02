package com.smartrecruit.job.infrastructure;

import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    /** Contrôle de propriété dans la requête : l'offre d'une autre entreprise est introuvable (404). */
    Optional<JobOffer> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<JobOffer> findByCompanyIdAndStatus(UUID companyId, JobStatus status, Pageable pageable);

    Page<JobOffer> findByCompanyIdAndStatusNot(UUID companyId, JobStatus status, Pageable pageable);

    List<JobOffer> findByCompanyIdAndStatus(UUID companyId, JobStatus status);

    /** Offres non archivées et offres publiées, par entreprise (administration, US-49). */
    @Query("""
            select o.companyId as companyId, count(o) as total,
                   sum(case when o.status = com.smartrecruit.job.domain.JobStatus.PUBLISHED then 1 else 0 end) as published
            from JobOffer o
            where o.companyId in :companyIds and o.status <> com.smartrecruit.job.domain.JobStatus.ARCHIVED
            group by o.companyId""")
    List<CompanyCount> countByCompany(@Param("companyIds") Collection<UUID> companyIds);

    interface CompanyCount {
        UUID getCompanyId();

        long getTotal();

        long getPublished();
    }
}
