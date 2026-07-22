package com.backend.analysis.dto;

import com.backend.analysis.domain.AnalysisResult;
import java.time.LocalDateTime;
import java.util.List;

// 재분석 API가 프론트에 내려주는 최종 응답
public record AnalysisReResponse(
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
        List<RequirementResponse> requirements
) {

    public static AnalysisReResponse of(
            AnalysisResult result,
            List<RequirementResponse> requirements
    ) {
        // 재분석 후 갱신된 Entity를 화면 응답 DTO로 조립
        return new AnalysisReResponse(
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
                requirements
        );
    }
}
