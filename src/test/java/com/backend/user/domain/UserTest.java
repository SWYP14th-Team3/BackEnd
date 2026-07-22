package com.backend.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("소셜 로그인 사용자는 이메일 없이 생성할 수 있다")
    void createSocialUserAllowsNullEmail() {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );

        assertThat(user.getEmail()).isNull();
        assertThat(user.getProvider()).isEqualTo(Provider.KAKAO);
        assertThat(user.getProviderId()).isEqualTo("kakao-provider-id");
    }
}
