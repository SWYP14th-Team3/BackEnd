package com.backend.analysis.dto;

import java.util.List;

// Gemini가 이력서와 채용공고를 비교한 결과
public record GeminiAnalysisResponse(
        String company,
        String position,
        GeminiAnalysisSummary summary,
        List<GeminiRequirementResult> requirements
) {
    public String overallLevel() {
        // summary 카운트를 기준으로 기존 DB의 overallLevel 값으로 변환
        if (summary == null) {
            return "MEDIUM";
        }

        if (summary.red_flag_count() > 0 || summary.gap_count() > summary.met_count()) {
            return "LOW";
        }

        if (summary.yellow_flag_count() > 0 || summary.partial_count() > 0) {
            return "MEDIUM";
        }

        return "HIGH";
    }
}
