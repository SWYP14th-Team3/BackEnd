package com.backend.analysis.dto.response;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobRequirementResponse {

    private Long requirementId;
    private RequirementCategory category;
    private String title;
    private String description;
    private String sourceText;
    private RequirementEvaluationResponse evaluation;

    public static JobRequirementResponse from(
            JobRequirement requirement,
            RequirementEvaluation evaluation
    ) {
        return JobRequirementResponse.builder()
                .requirementId(requirement.getId())
                .category(requirement.getCategory())
                .title(requirement.getTitle())
                .description(requirement.getDescription())
                .sourceText(requirement.getSourceText())
                .evaluation(RequirementEvaluationResponse.from(evaluation))
                .build();
    }
}