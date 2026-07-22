package com.backend.analysis.repository;

import com.backend.analysis.domain.RequirementEvaluation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// requirement_evaluation 테이블 CRUD
public interface RequirementEvaluationRepository extends JpaRepository<RequirementEvaluation, Long> {

    // 재분석 시 기존 평가를 분석 결과 ID 기준으로 조회
    List<RequirementEvaluation> findByAnalysisResultIdOrderByRequirementIdAsc(Long analysisResultId);
}
