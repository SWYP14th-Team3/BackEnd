package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.Satisfaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalysisSatisfactionResponse {

    private Long analysisResultId;
    private Satisfaction satisfaction;
    private LocalDateTime updatedAt;

    public static AnalysisSatisfactionResponse from(AnalysisResult analysisResult) {
        return AnalysisSatisfactionResponse.builder()
                .analysisResultId(analysisResult.getId())
                .satisfaction(analysisResult.getSatisfaction())
                .updatedAt(analysisResult.getUpdatedAt())
                .build();
    }
}
