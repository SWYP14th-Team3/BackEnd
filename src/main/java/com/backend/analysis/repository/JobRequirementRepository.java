package com.backend.analysis.repository;

import com.backend.analysis.domain.JobRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// job_requirement 테이블 CRUD
public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {

    // 재분석 시 기존 요건을 분석 결과 ID 기준으로 조회
    List<JobRequirement> findByAnalysisResultIdOrderByIdAsc(Long analysisResultId);
}
