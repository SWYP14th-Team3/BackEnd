package com.backend.analysis.dto;

// 재분석 요청 DTO
public record AnalysisReRequest(
        // 갱신할 analysis_result row ID
        Long analysis_result_id,
        // 화면에서 수정한 이력서 마크다운
        String resume_content,
        // 화면에서 전달한 원본 채용공고 마크다운
        String jd_content
) {
}
