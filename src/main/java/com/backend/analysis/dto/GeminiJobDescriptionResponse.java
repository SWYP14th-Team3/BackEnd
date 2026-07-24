package com.backend.analysis.dto;

// Gemini가 채용공고 입력에서 원문 텍스트 확보에 성공했는지와 원문을 반환
public record GeminiJobDescriptionResponse(
        boolean success,
        String raw_text
) {

    public String companyName() {
        return null;
    }

    public String positionTitle() {
        return null;
    }

    public String jobPlatform() {
        return null;
    }

    public String jdContent() {
        return raw_text;
    }
}
