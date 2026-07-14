package com.backend.analysis.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisCreateResponse {

    private Long analysisResultId;

    public static AnalysisCreateResponse of(Long analysisResultId) {
        return AnalysisCreateResponse.builder()
                .analysisResultId(analysisResultId)
                .build();
    }
}