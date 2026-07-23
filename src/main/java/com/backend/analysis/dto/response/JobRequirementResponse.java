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
    private String requirementType;
    private String category;
    private String title;
    private String description;
    private String jdEvidence;
    private Integer inputOrder;
    private RequirementEvaluationResponse evaluation;

    public static JobRequirementResponse from(
            JobRequirement requirement,
            RequirementEvaluation evaluation
    ) {
        return JobRequirementResponse.builder()
                .requirementId(requirement.getId())
                .requirementType(requirement.getRequirementType().name())
                .category(toCategoryLabel(requirement.getCategory()))
                .title(requirement.getTitle())
                .description(requirement.getDescription())
                .jdEvidence(requirement.getJdEvidence())
                .inputOrder(requirement.getInputOrder())
                .evaluation(RequirementEvaluationResponse.from(evaluation))
                .build();
    }

    private static String toCategoryLabel(RequirementCategory category) {
        return switch (category) {
            case QUALIFICATION -> "자격요건";
            case WORK_COMPETENCY -> "업무역량";
            case DOMAIN -> "도메인";
            case PREFERENCE -> "우대사항";
        };
    }
}
