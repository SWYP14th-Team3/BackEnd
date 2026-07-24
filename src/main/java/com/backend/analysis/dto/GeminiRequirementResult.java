package com.backend.analysis.dto;

// Gemini가 추출한 요건 1개와 그 평가 결과
public record GeminiRequirementResult(
        String req_id,
        String content,
        String importance,
        String jd_evidence,
        String resume_evidence,
        String judge_reason,
        String id,
        String text,
        String type,
        String status,
        String flag,
        String evidence,
        String feedback,
        String suggestion
) {
    public String reqId() {
        return req_id != null ? req_id : id;
    }

    public String category() {
        // importance를 우선 사용하고, 기존 응답 호환을 위해 type도 허용
        return importance != null ? importance : type;
    }

    public String title() {
        // content를 우선 사용하고, 기존 응답 호환을 위해 text도 허용
        return content != null ? content : text;
    }

    public String description() {
        return title();
    }

    public String sourceText() {
        // jd_evidence를 우선 사용하고, 없으면 요건 문구를 근거로 사용
        return jd_evidence != null ? jd_evidence : title();
    }

    public String matchStatus() {
        // Gemini의 status 값을 DB matchStatus 의미로 사용
        return status;
    }

    public String resumeEvidence() {
        return resume_evidence != null ? resume_evidence : evidence;
    }

    public String judgeReason() {
        return judge_reason != null ? judge_reason : feedback;
    }

    public String revisionSuggestion() {
        // Gemini의 suggestion 값을 수정 제안으로 사용
        return suggestion;
    }
}
