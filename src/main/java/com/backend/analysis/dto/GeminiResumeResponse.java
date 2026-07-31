package com.backend.analysis.dto;

// Gemini가 PDF 추출 텍스트를 읽고 정리한 결과
public record GeminiResumeResponse(
        String resumeContent,
        String resumeFileName
) {
}
