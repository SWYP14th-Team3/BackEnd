package com.backend.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisResumeSaveRequest {

    @NotBlank(message = "resumeCurrentText는 필수입니다.")
    private String resumeCurrentText;
}
