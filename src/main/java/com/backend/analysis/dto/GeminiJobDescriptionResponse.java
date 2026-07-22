package com.backend.analysis.dto;

// Gemini가 채용공고 URL/이미지를 읽고 정리한 결과
public record GeminiJobDescriptionResponse(
        String companyName,
        String positionTitle,
        String jobPlatform,
        String jdContent
) {
}
