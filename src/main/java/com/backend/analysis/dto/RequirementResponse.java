package com.backend.analysis.dto;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;

// 요건과 평가를 합쳐 화면 카드 1개로 내려주는 응답
public record RequirementResponse(
        Long requirementId,
        Long evaluationId,
        Long analysisResultId,
        String category,
        String title,
        String description,
        String sourceText,
        String matchStatus,
        String resumeEvidence,
        String feedback,
        String revisionSuggestion
) {

    public static RequirementResponse from(
            JobRequirement requirement,
            RequirementEvaluation evaluation
    ) {
        // JOB_REQUIREMENT와 REQUIREMENT_EVALUATION을 하나의 응답으로 조립
        return new RequirementResponse(
                requirement.getId(),
                evaluation.getId(),
                evaluation.getAnalysisResultId(),
                requirement.getCategory(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getSourceText(),
                evaluation.getMatchStatus(),
                evaluation.getResumeEvidence(),
                evaluation.getFeedback(),
                evaluation.getRevisionSuggestion()
        );
    }
}
