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

    private Integer retryCount;
    private Integer remainingRetryCount;

    private Satisfaction satisfaction;

    private JobInputType jobInputType;
    private String jobUrl;
    private String jobPostingRaw;
    private String resumeOriginalText;
    private String resumeCurrentText;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastSavedAt;

    private List<JobRequirementResponse> requirements;

    public static AnalysisDetailResponse from(
            AnalysisResult analysisResult,
            List<JobRequirementResponse> requirements,
            JobInputType jobInputType,
            String jobUrl,
            String jobPostingRaw,
            String resumeOriginalText
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
                .retryCount(retryCount)
                .remainingRetryCount(Math.max(0, MAX_RETRY_COUNT - retryCount))
                .satisfaction(analysisResult.getSatisfaction())
                .jobInputType(jobInputType)
                .jobUrl(jobUrl)
                .jobPostingRaw(jobPostingRaw)
                .resumeOriginalText(resumeOriginalText)
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .createdAt(analysisResult.getCreatedAt())
                .updatedAt(analysisResult.getUpdatedAt())
                .lastSavedAt(analysisResult.getLastSavedAt())
                .requirements(requirements)
                .build();
    }
}
