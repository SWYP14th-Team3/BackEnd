package com.backend.analysis.dto;

// Gemini가 이력서 PDF를 읽고 정리한 결과
public record GeminiResumeResponse(
        String resumeContent,
        String resumeFileName
) {
}
