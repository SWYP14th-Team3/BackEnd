package com.backend.analysis.dto;

import java.util.List;

// Gemini가 이력서와 채용공고를 비교한 결과
public record GeminiAnalysisResponse(
        Boolean analyzable,
        String fail_side,
        String company,
        String position,
        GeminiAnalysisSummary summary,
        List<GeminiRequirementResult> requirements
) {
    public boolean isAnalyzable() {
        return analyzable == null || analyzable;
    }
}
