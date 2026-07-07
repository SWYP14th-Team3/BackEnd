package com.backend.auth.dto.response;

import com.backend.user.domain.Provider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SocialUserInfo {

    private String email;
    private String name;
    private String providerId;
    private Provider provider;
}