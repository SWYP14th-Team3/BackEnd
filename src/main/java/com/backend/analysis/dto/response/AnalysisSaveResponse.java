package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalysisSaveResponse {

    private Long analysisResultId;
    private String resumeCurrentText;
    private LocalDateTime updatedAt;

    public static AnalysisSaveResponse from(AnalysisResult analysisResult) {
        return AnalysisSaveResponse.builder()
                .analysisResultId(analysisResult.getId())
                .resumeCurrentText(analysisResult.getResumeCurrentText())
                .updatedAt(analysisResult.getUpdatedAt())
                .build();
    }
}
