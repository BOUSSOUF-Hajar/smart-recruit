package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.candidate.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    Optional<Resume> findByCandidateIdAndActiveTrue(UUID candidateId);
}
