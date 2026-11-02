package com.smartrecruit.company.infrastructure;

import com.smartrecruit.company.domain.RecruiterProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, UUID> {

    long countByCompanyId(UUID companyId);

    List<RecruiterProfile> findByCompanyId(UUID companyId);

    /** Nombre de recruteurs par entreprise, pour une page de la liste (une requête). */
    @Query("""
            select p.companyId as companyId, count(p) as total from RecruiterProfile p
            where p.companyId in :companyIds group by p.companyId""")
    List<CompanyCount> countByCompany(@Param("companyIds") Collection<UUID> companyIds);

    interface CompanyCount {
        UUID getCompanyId();

        long getTotal();
    }
}
