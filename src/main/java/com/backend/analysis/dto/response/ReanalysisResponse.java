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

    private OverallLevel previousOverallLevel;
    private Integer previousRedCount;
    private Integer previousYellowCount;
    private Integer previousGreenCount;

    private OverallLevel overallLevel;

    private Integer redCount;
    private Integer yellowCount;
    private Integer greenCount;

    private LocalDateTime lastReanalyzedAt;

    private Integer retryCount;
    private Integer remainingRetryCount;

    private String resumeCurrentText;

    private LocalDateTime resumeLastSavedAt;
    private LocalDateTime finalSavedAt;
    private LocalDateTime updatedAt;

    private List<JobRequirementResponse> requirements;

    public static ReanalysisResponse from(
            AnalysisResult analysisResult,
            List<JobRequirementResponse> requirements
    ) {
        int retryCount = analysisResult.getRetryCount();

        return ReanalysisResponse.builder()
                .analysisResultId(analysisResult.getId())
                .previousOverallLevel(analysisResult.getPreviousOverallLevel())
                .previousRedCount(analysisResult.getPreviousRedCount())
                .previousYellowCount(analysisResult.getPreviousYellowCount())
                .previousGreenCount(analysisResult.getPreviousGreenCount())
                .overallLevel(analysisResult.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .lastReanalyzedAt(analysisResult.getLastReanalyzedAt())
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .resumeLastSavedAt(analysisResult.getUserResume().getLastSavedAt())
                .finalSavedAt(analysisResult.getFinalSavedAt())
                .updatedAt(analysisResult.getUpdatedAt())
                .requirements(requirements)
                .build();
    }

    public static ReanalysisResponse unchanged(
            AnalysisResult analysisResult,
            List<JobRequirementResponse> requirements
    ) {
        int retryCount = analysisResult.getRetryCount();

        return ReanalysisResponse.builder()
                .analysisResultId(analysisResult.getId())
                .previousOverallLevel(analysisResult.getOverallLevel())
                .previousRedCount(analysisResult.getRedCount())
                .previousYellowCount(analysisResult.getYellowCount())
                .previousGreenCount(analysisResult.getGreenCount())
                .overallLevel(analysisResult.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .lastReanalyzedAt(analysisResult.getLastReanalyzedAt())
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .resumeLastSavedAt(analysisResult.getUserResume().getLastSavedAt())
                .finalSavedAt(analysisResult.getFinalSavedAt())
                .updatedAt(analysisResult.getUpdatedAt())
                .requirements(requirements)
                .build();
    }
}
