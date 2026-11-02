package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.candidate.domain.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
}
