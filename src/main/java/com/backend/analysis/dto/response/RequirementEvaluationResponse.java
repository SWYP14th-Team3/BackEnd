package com.backend.analysis.dto.response;

import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.RequirementEvaluation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RequirementEvaluationResponse {

    private Long evaluationId;
    private MatchStatus matchStatus;
    private String resumeEvidence;
    private String feedback;
    private String revisionSuggestion;

    public static RequirementEvaluationResponse from(RequirementEvaluation evaluation) {
        return RequirementEvaluationResponse.builder()
                .evaluationId(evaluation.getId())
                .matchStatus(evaluation.getMatchStatus())
                .resumeEvidence(evaluation.getResumeEvidence())
                .feedback(evaluation.getFeedback())
                .revisionSuggestion(evaluation.getRevisionSuggestion())
                .build();
    }
}