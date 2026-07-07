package com.backend.auth.dto.response;

import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthMeResponse {

    private Long userId;
    private String email;
    private String name;
    private Provider provider;

    public static AuthMeResponse from(User user) {
        return AuthMeResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .provider(user.getProvider())
                .build();
    }
}