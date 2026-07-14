package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {

    List<JobRequirement> findAllByAnalysisResultOrderByIdAsc(AnalysisResult analysisResult);
}