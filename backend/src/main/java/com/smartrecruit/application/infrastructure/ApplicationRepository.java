package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.domain.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /** Contrôle de propriété dans la requête (RG-06) : la candidature d'un autre candidat n'est jamais chargée. */
    Optional<Application> findByIdAndCandidateId(UUID id, UUID candidateId);

    Optional<Application> findByJobOfferIdAndCandidateId(UUID jobOfferId, UUID candidateId);

    boolean existsByJobOfferIdAndCandidateId(UUID jobOfferId, UUID candidateId);

    boolean existsByJobOfferId(UUID jobOfferId);

    boolean existsByResumeId(UUID resumeId);

    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);

    @Query("select a.id from Application a where a.candidateId = :candidateId")
    List<UUID> findIdsByCandidateId(UUID candidateId);

    @Query("""
            select a.jobOfferId as jobOfferId, a.status as status, count(a) as total
            from Application a where a.jobOfferId in :jobOfferIds
            group by a.jobOfferId, a.status""")
    List<StatusCount> countByStatus(Collection<UUID> jobOfferIds);

    /**
     * Copie du dernier score (US-42) par une mise à jour ciblée : ni la version (verrou optimiste) ni la
     * date de modification ne changent, donc un recruteur qui lit le dossier n'est pas mis en conflit.
     */
    @Modifying
    @Query("update Application a set a.lastMatchScore = :score, a.lastMatchedAt = :at where a.id = :id")
    int recordMatchScore(UUID id, short score, java.time.Instant at);

    interface StatusCount {
        UUID getJobOfferId();

        ApplicationStatus getStatus();

        long getTotal();
    }
}
