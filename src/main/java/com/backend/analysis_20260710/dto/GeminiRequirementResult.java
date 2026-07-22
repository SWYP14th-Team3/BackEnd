
package com.backend.analysis_20260710.dto;

public record GeminiRequirementResult(
        String category,
        String title,
        String description,
        String sourceText,
        String matchStatus,
        String resumeEvidence,
        String feedback,
        String revisionSuggestion
) {
}