package com.backend.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisResumeSaveRequest {

    @NotBlank(message = "저장할 이력서 텍스트는 필수입니다.")
    private String resumeCurrentText;
}