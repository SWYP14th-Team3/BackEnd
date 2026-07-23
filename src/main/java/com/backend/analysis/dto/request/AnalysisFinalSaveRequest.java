package com.backend.analysis.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisFinalSaveRequest {

    @NotNull(message = "resumeCurrentText는 필수입니다.")
    private String resumeCurrentText;
}
