package com.backend.analysis.dto;

// Gemini가 red/yellow 요건의 우선순위 점수를 계산한 결과
public record GeminiPriorityScoreResult(
        String req_id,
        Integer effect_score,
        Integer effort_score,
        String reason
) {
}
