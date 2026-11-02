package com.smartrecruit.interview.infrastructure;

import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.domain.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID>, JpaSpecificationExecutor<Interview> {

    /** Contrôle de propriété dans la requête (RG-05). */
    Optional<Interview> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Interview> findByApplicationIdOrderByStartsAtAsc(UUID applicationId);

    List<Interview> findByApplicationIdAndStatus(UUID applicationId, InterviewStatus status);

    boolean existsByApplicationIdAndStatus(UUID applicationId, InterviewStatus status);

    List<Interview> findByApplicationIdInAndStartsAtGreaterThanEqualOrderByStartsAtAsc(Collection<UUID> applicationIds,
                                                                                       Instant from);

    List<Interview> findByApplicationIdInAndStartsAtLessThanOrderByStartsAtDesc(Collection<UUID> applicationIds,
                                                                                Instant before);
}
