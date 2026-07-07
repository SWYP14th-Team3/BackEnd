package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.Satisfaction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisSatisfactionResponse {

    private Long analysisResultId;
    private Satisfaction satisfaction;

    public static AnalysisSatisfactionResponse from(AnalysisResult analysisResult) {
        return AnalysisSatisfactionResponse.builder()
                .analysisResultId(analysisResult.getId())
                .satisfaction(analysisResult.getSatisfaction())
                .build();
    }
}