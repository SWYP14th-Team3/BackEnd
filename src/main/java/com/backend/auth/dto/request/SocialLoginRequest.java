package com.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {

    @NotBlank(message = "authorizationCode는 필수입니다.")
    private String authorizationCode;
}
