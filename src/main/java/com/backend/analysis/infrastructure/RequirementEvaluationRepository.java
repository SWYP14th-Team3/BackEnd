package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RequirementEvaluationRepository extends JpaRepository<RequirementEvaluation, Long> {

    Optional<RequirementEvaluation> findByJobRequirement(JobRequirement jobRequirement);

    @Modifying
    @Query("""
            delete from RequirementEvaluation evaluation
            where evaluation.analysisResult.id in (
                select analysisResult.id
                from AnalysisResult analysisResult
                where analysisResult.user.id = :userId
            )
            """)
    void deleteAllByUserId(@Param("userId") Long userId);
}
