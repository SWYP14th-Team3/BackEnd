package com.backend.analysis_20260710.dto;

import com.backend.analysis_20260710.domain.RequirementEvaluation;

public record RequirementResponse(
        Long requirementId,
        Long evaluationId,
        String category,
        String title,
        String description,
        String sourceText,
        String matchStatus,
        String resumeEvidence,
        String feedback,
        String revisionSuggestion
) {

    public static RequirementResponse from(RequirementEvaluation evaluation) {
        return new RequirementResponse(
                evaluation.getRequirement().getId(),
                evaluation.getId(),
                evaluation.getRequirement().getCategory(),
                evaluation.getRequirement().getTitle(),
                evaluation.getRequirement().getDescription(),
                evaluation.getRequirement().getSourceText(),
                evaluation.getMatchStatus(),
                evaluation.getResumeEvidence(),
                evaluation.getFeedback(),
                evaluation.getRevisionSuggestion()
        );
    }
}