package com.backend.analysis.dto;

// Gemini가 추출한 요건 1개와 그 평가 결과
public record GeminiRequirementResult(
        String id,
        String text,
        String type,
        String status,
        String flag,
        String evidence,
        String feedback,
        String suggestion
) {
    public String category() {
        // Gemini의 type 값을 DB category 의미로 사용
        return type;
    }

    public String title() {
        // Gemini의 text 값을 화면 요건 제목으로 사용
        return text;
    }

    public String description() {
        // 별도 설명 필드가 없으므로 원문 요건을 설명으로 사용
        return text;
    }

    public String sourceText() {
        // Gemini의 text 값을 공고 원문 근거로 사용
        return text;
    }

    public String matchStatus() {
        // Gemini의 status 값을 DB matchStatus 의미로 사용
        return status;
    }

    public String resumeEvidence() {
        // Gemini의 evidence 값을 이력서 근거로 사용
        return evidence;
    }

    public String revisionSuggestion() {
        // Gemini의 suggestion 값을 수정 제안으로 사용
        return suggestion;
    }
}
