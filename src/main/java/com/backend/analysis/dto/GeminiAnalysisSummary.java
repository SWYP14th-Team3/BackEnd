package com.backend.analysis.dto;

// Gemini 분석 요약 결과
public record GeminiAnalysisSummary(
        int met_count,
        int partial_count,
        int gap_count,
        int red_flag_count,
        int yellow_flag_count,
        String top_message
) {
}
