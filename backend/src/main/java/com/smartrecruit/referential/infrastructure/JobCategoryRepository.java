package com.smartrecruit.referential.infrastructure;

import com.smartrecruit.referential.domain.JobCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobCategoryRepository extends JpaRepository<JobCategory, UUID> {

    List<JobCategory> findByActiveTrueOrderByNameAsc();

    List<JobCategory> findAllByOrderByNameAsc();
}
