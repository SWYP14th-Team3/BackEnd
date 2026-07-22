package com.backend.analysis.repository;

import com.backend.analysis.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

// analysis_result 테이블 CRUD
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
}
