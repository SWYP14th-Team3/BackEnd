package com.backend.analysis_20260710.repository;

import com.backend.analysis_20260710.domain.RequirementEvaluation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementEvaluationRepository extends JpaRepository<RequirementEvaluation, Long> {

    List<RequirementEvaluation> findByRequirement_AnalysisResult_IdOrderByRequirement_IdAsc(Long analysisResultId);
}
