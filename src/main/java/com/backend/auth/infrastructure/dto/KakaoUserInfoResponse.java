package com.backend.auth.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfoResponse {

    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {

        private String email;
        private Profile profile;
    }

    @Getter
    @NoArgsConstructor
    public static class Profile {

        private String nickname;
    }

    public String getEmail() {
        if (kakaoAccount == null) {
            return null;
        }
        return kakaoAccount.getEmail();
    }

    public String getNickname() {
        if (kakaoAccount == null || kakaoAccount.getProfile() == null) {
            return null;
        }
        return kakaoAccount.getProfile().getNickname();
    }
}