package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.domain.AiTask;
import com.smartrecruit.ai.domain.AiTaskStatus;
import com.smartrecruit.ai.domain.AiTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    /** Contrôle de propriété dans la requête (RG-05). */
    Optional<AiTask> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<AiTask> findFirstByApplicationIdAndTypeAndStatusInOrderByCreatedAtDesc(UUID applicationId,
                                                                                     AiTaskType type,
                                                                                     Collection<AiTaskStatus> statuses);

    List<AiTask> findByStatusIn(Collection<AiTaskStatus> statuses);
}
