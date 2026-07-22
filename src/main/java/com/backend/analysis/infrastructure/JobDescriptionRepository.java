package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {
}
