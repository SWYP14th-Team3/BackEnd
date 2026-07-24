package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.AnalysisAttempt;
import com.backend.analysis.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisAttemptRepository extends JpaRepository<AnalysisAttempt, Long> {

    List<AnalysisAttempt> findAllByAnalysisResultOrderByAttemptNoAsc(AnalysisResult analysisResult);

    Optional<AnalysisAttempt> findTopByAnalysisResultOrderByAttemptNoDesc(AnalysisResult analysisResult);
}
