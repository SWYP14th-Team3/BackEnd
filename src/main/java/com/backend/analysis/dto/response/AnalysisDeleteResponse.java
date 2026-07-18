package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalysisDeleteResponse {

    private Long analysisResultId;
    private Boolean deleted;
    private LocalDateTime deletedAt;

    public static AnalysisDeleteResponse from(AnalysisResult analysisResult) {
        return AnalysisDeleteResponse.builder()
                .analysisResultId(analysisResult.getId())
                .deleted(true)
                .deletedAt(analysisResult.getDeletedAt())
                .build();
    }
}
