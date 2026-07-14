package com.backend.analysis.infrastructure;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequirementEvaluationRepository extends JpaRepository<RequirementEvaluation, Long> {

    Optional<RequirementEvaluation> findByJobRequirement(JobRequirement jobRequirement);
}