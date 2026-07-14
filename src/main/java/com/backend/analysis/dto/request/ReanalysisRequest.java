package com.backend.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReanalysisRequest {

    @NotBlank(message = "재분석할 이력서 텍스트는 필수입니다.")
    private String resumeCurrentText;
}