package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.Satisfaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AnalysisDetailResponse {

    private static final int MAX_RETRY_COUNT = 5;

    private Long analysisResultId;

    private String companyName;
    private String positionTitle;

    private OverallLevel overallLevel;
    private Integer redCount;
    private Integer yellowCount;
    private Integer greenCount;

    private OverallLevel previousOverallLevel;
    private Integer previousRedCount;
    private Integer previousYellowCount;
    private Integer previousGreenCount;
    private LocalDateTime lastReanalyzedAt;

    private Integer retryCount;
    private Integer remainingRetryCount;

    private Satisfaction satisfaction;

    private JobInputType jobInputType;
    private String jobUrl;
    private String jobPlatform;
    private String jobOriginalText;
    private String jobSummaryText;
    private String resumeCurrentText;
    private String resumeFileName;
    private Long resumeFileSize;
    private LocalDateTime resumeLastSavedAt;
    private LocalDateTime finalSavedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<JobRequirementResponse> requirements;

    public static AnalysisDetailResponse from(
            AnalysisResult analysisResult,
            List<JobRequirementResponse> requirements
    ) {
        int retryCount = analysisResult.getRetryCount();

        return AnalysisDetailResponse.builder()
                .analysisResultId(analysisResult.getId())
                .companyName(analysisResult.getJobDescription().getCompanyName())
                .positionTitle(analysisResult.getJobDescription().getPositionTitle())
                .overallLevel(analysisResult.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .previousOverallLevel(analysisResult.getPreviousOverallLevel())
                .previousRedCount(analysisResult.getPreviousRedCount())
                .previousYellowCount(analysisResult.getPreviousYellowCount())
                .previousGreenCount(analysisResult.getPreviousGreenCount())
                .lastReanalyzedAt(analysisResult.getLastReanalyzedAt())
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .satisfaction(analysisResult.getSatisfaction())
                .jobInputType(analysisResult.getJobDescription().getJobInputType())
                .jobUrl(analysisResult.getJobDescription().getJobUrl())
                .jobPlatform(analysisResult.getJobDescription().getJobPlatform())
                .jobOriginalText(analysisResult.getJobDescription().getJdOriginalText())
                .jobSummaryText(analysisResult.getJobDescription().getJdSummaryText())
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .resumeFileName(analysisResult.getUserResume().getResumeFileName())
                .resumeFileSize(analysisResult.getUserResume().getResumeFileSize())
                .resumeLastSavedAt(analysisResult.getUserResume().getLastSavedAt())
                .finalSavedAt(analysisResult.getFinalSavedAt())
                .createdAt(analysisResult.getCreatedAt())
                .updatedAt(analysisResult.getUpdatedAt())
                .requirements(requirements)
                .build();
    }
}
