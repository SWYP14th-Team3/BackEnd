package com.backend.analysis.dto;

// Gemini가 카드에 표시할 제목과 피드백을 생성한 결과
public record GeminiCardContentResult(
        String req_id,
        String status,
        String title,
        String feedback
) {
}
