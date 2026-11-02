package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.candidate.domain.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperienceRepository extends JpaRepository<Experience, UUID> {

    List<Experience> findByCandidateId(UUID candidateId);

    /** Contrôle de propriété dans la requête : l'expérience d'un autre candidat est introuvable (404). */
    Optional<Experience> findByIdAndCandidateId(UUID id, UUID candidateId);

    long countByCandidateId(UUID candidateId);
}
