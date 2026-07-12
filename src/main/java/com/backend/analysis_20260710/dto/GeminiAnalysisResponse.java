package com.backend.analysis_20260710.dto;

import java.util.List;

public record GeminiAnalysisResponse(
        String companyName,
        String positionTitle,
        String overallLevel,
        String resumeOriginalText,
        List<GeminiRequirementResult> requirements
) {
}