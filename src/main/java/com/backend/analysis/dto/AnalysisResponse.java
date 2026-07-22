package com.backend.analysis.dto;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.UserResume;
import java.time.LocalDateTime;
import java.util.List;

// 분석 API가 프론트에 내려주는 최종 응답
public record AnalysisResponse(
        Long analysisResultId,
        Long userId,
        Long resumeNum,
        Long jdNum,
        String companyName,
        String positionTitle,
        String overallLevel,
        int redCount,
        int yellowCount,
        int greenCount,
        int retryCount,
        String satisfaction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastSavedAt,
        LocalDateTime deletedAt,
        UserResumeResponse userResume,
        JobDescriptionResponse jobDescription,
        List<RequirementResponse> requirements
) {

    public static AnalysisResponse of(
            AnalysisResult result,
            UserResume resume,
            JobDescription jobDescription,
            List<RequirementResponse> requirements
    ) {
        // Entity 여러 개를 화면 응답 DTO 하나로 조립
        return new AnalysisResponse(
                result.getId(),
                result.getUserId(),
                result.getResumeNum(),
                result.getJdNum(),
                result.getCompanyName(),
                result.getPositionTitle(),
                result.getOverallLevel(),
                result.getRedCount(),
                result.getYellowCount(),
                result.getGreenCount(),
                result.getRetryCount(),
                result.getSatisfaction(),
                result.getCreatedAt(),
                result.getUpdatedAt(),
                result.getLastSavedAt(),
                result.getDeletedAt(),
                UserResumeResponse.from(resume),
                JobDescriptionResponse.from(jobDescription),
                requirements
        );
    }
}
