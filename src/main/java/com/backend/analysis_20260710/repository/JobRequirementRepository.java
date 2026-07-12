package com.backend.analysis_20260710.repository;

import com.backend.analysis_20260710.domain.JobRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {

    List<JobRequirement> findByAnalysisResultIdOrderByIdAsc(Long analysisResultId);
}