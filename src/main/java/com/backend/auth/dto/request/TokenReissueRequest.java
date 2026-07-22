package com.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenReissueRequest {

    @NotBlank(message = "Refresh Token이 요청에 포함되지 않았습니다.")
    private String refreshToken;
}
