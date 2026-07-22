package com.backend.analysis.repository;

import com.backend.analysis.domain.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

// job_description 테이블 CRUD
public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {
}
