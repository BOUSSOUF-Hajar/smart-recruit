package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.candidate.domain.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EducationRepository extends JpaRepository<Education, UUID> {

    List<Education> findByCandidateId(UUID candidateId);

    Optional<Education> findByIdAndCandidateId(UUID id, UUID candidateId);
}
