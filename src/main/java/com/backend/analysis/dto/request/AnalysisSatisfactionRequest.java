package com.backend.analysis.dto.request;

import com.backend.analysis.domain.Satisfaction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisSatisfactionRequest {

    @NotNull(message = "만족도 값은 필수입니다.")
    private Satisfaction satisfaction;
}