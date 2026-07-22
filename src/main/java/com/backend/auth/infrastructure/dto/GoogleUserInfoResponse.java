package com.backend.auth.infrastructure.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleUserInfoResponse {

    private String sub;
    private String email;
    private String name;
}
