package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.OverallLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ReanalysisResponse {

    private static final int MAX_RETRY_COUNT = 5;

    private Long analysisResultId;

    private OverallLevel overallLevel;

    private Integer redCount;
    private Integer yellowCount;
    private Integer greenCount;

    private Integer retryCount;
    private Integer remainingRetryCount;

    private String resumeCurrentText;

    private LocalDateTime updatedAt;
    private LocalDateTime lastSavedAt;

    private List<JobRequirementResponse> requirements;

    public static ReanalysisResponse from(
            AnalysisResult analysisResult,
            List<JobRequirementResponse> requirements
    ) {
        int retryCount = analysisResult.getRetryCount();

        return ReanalysisResponse.builder()
                .analysisResultId(analysisResult.getId())
                .overallLevel(analysisResult.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .updatedAt(analysisResult.getUpdatedAt())
                .lastSavedAt(analysisResult.getLastSavedAt())
                .requirements(requirements)
                .build();
    }
}
