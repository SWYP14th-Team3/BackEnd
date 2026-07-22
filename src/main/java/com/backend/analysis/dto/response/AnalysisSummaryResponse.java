package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.OverallLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalysisSummaryResponse {

    private static final int MAX_RETRY_COUNT = 5;

    private Long analysisResultId;
    private String companyName;
    private String positionTitle;
    private LocalDateTime createdAt;

    private Integer retryCount;
    private Integer remainingRetryCount;

    private Integer redCount;
    private Integer yellowCount;
    private Integer greenCount;

    private OverallLevel overallLevel;

    public static AnalysisSummaryResponse from(AnalysisResult analysisResult) {
        int retryCount = analysisResult.getRetryCount();

        return AnalysisSummaryResponse.builder()
                .analysisResultId(analysisResult.getId())
                .companyName(analysisResult.getJobDescription().getCompanyName())
                .positionTitle(analysisResult.getJobDescription().getPositionTitle())
                .createdAt(analysisResult.getCreatedAt())
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .overallLevel(analysisResult.getOverallLevel())
                .build();
    }
}
