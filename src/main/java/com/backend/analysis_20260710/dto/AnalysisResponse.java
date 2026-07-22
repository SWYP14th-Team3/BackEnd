package com.backend.analysis_20260710.dto;

import com.backend.analysis_20260710.domain.AnalysisResult;
import java.util.List;

public record AnalysisResponse(
        Long analysisResultId,
        String userId,
        String jobUrl,
        String jobPlatform,
        String companyName,
        String positionTitle,
        String overallLevel,
        int redCount,
        int yellowCount,
        int greenCount,
        List<RequirementResponse> requirements
) {

    public static AnalysisResponse of(
            AnalysisResult result,
            List<RequirementResponse> requirements
    ) {
        return new AnalysisResponse(
                result.getId(),
                result.getUserId(),
                result.getJobUrl(),
                result.getJobPlatform(),
                result.getCompanyName(),
                result.getPositionTitle(),
                result.getOverallLevel(),
                result.getRedCount(),
                result.getYellowCount(),
                result.getGreenCount(),
                requirements
        );
    }
}
