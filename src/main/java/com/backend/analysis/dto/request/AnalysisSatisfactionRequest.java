package com.backend.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisSatisfactionRequest {

    @NotBlank(message = "satisfaction 값이 올바르지 않습니다.")
    private String satisfaction;
}
