package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnalysisFinalSaveResponse {

    private Long analysisResultId;
    private Boolean saved;
    private String resumeCurrentText;
    private LocalDateTime resumeLastSavedAt;
    private LocalDateTime finalSavedAt;

    public static AnalysisFinalSaveResponse from(AnalysisResult analysisResult) {
        return AnalysisFinalSaveResponse.builder()
                .analysisResultId(analysisResult.getId())
                .saved(true)
                .resumeCurrentText(analysisResult.getUserResume().getResumeContent())
                .resumeLastSavedAt(analysisResult.getUserResume().getLastSavedAt())
                .finalSavedAt(analysisResult.getFinalSavedAt())
                .build();
    }
}
