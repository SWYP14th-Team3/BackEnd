package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {

    List<JobRequirement> findAllByAnalysisResultOrderByIdAsc(AnalysisResult analysisResult);

    List<JobRequirement> findAllByAnalysisResultOrderByInputOrderAscIdAsc(AnalysisResult analysisResult);

    @Modifying
    @Query("""
            delete from JobRequirement requirement
            where requirement.analysisResult.id in (
                select analysisResult.id
                from AnalysisResult analysisResult
                where analysisResult.user.id = :userId
            )
            """)
    void deleteAllByUserId(@Param("userId") Long userId);
}
