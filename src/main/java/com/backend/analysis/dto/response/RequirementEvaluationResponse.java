package com.backend.analysis.dto.response;

import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.RequirementEvaluation;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RequirementEvaluationResponse {

    private Long evaluationId;
    private MatchStatus matchStatus;
    private String displayTitle;
    private String resumeEvidence;
    private String judgeReason;
    private String feedback;
    private String revisionSuggestion;
    private Integer effectScore;
    private Integer effortScore;
    private BigDecimal priorityScore;
    private Integer sortOrder;

    public static RequirementEvaluationResponse from(RequirementEvaluation evaluation) {
        return RequirementEvaluationResponse.builder()
                .evaluationId(evaluation.getId())
                .matchStatus(evaluation.getMatchStatus())
                .displayTitle(evaluation.getDisplayTitle())
                .resumeEvidence(evaluation.getResumeEvidence())
                .judgeReason(evaluation.getJudgeReason())
                .feedback(evaluation.getFeedback())
                .revisionSuggestion(evaluation.getRevisionSuggestion())
                .effectScore(evaluation.getEffectScore())
                .effortScore(evaluation.getEffortScore())
                .priorityScore(evaluation.getPriorityScore())
                .sortOrder(evaluation.getSortOrder())
                .build();
    }
}
