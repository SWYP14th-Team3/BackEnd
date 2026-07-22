package com.backend.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

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

    @Test
    @DisplayName("약관 동의 정보가 없으면 필수 약관 동의가 필요하다")
    void isTermsRequiredWhenAgreementIsMissing() {
        User user = User.createSocialUser(
                "test@example.com",
                Provider.GOOGLE,
                "google-provider-id",
                "테스트사용자"
        );

        assertThat(user.isTermsRequired("v1", "v1")).isTrue();
    }

    @Test
    @DisplayName("약관 동의 정보와 버전이 최신이면 필수 약관 동의가 필요하지 않다")
    void isTermsNotRequiredWhenAgreementVersionIsCurrent() {
        User user = User.createSocialUser(
                "test@example.com",
                Provider.GOOGLE,
                "google-provider-id",
                "테스트사용자"
        );

        ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "privacyAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "termsVersion", "v1");
        ReflectionTestUtils.setField(user, "privacyVersion", "v1");

        assertThat(user.isTermsRequired("v1", "v1")).isFalse();
    }

    @Test
    @DisplayName("약관 동의 시각과 버전을 저장할 수 있다")
    void agreeTermsStoresAgreementTimeAndVersions() {
        User user = User.createSocialUser(
                "test@example.com",
                Provider.GOOGLE,
                "google-provider-id",
                "테스트사용자"
        );
        LocalDateTime agreedAt = LocalDateTime.of(2026, 7, 23, 16, 30);

        user.agreeTerms(agreedAt, "v1", "v1");

        assertThat(user.getTermsAgreedAt()).isEqualTo(agreedAt);
        assertThat(user.getPrivacyAgreedAt()).isEqualTo(agreedAt);
        assertThat(user.getTermsVersion()).isEqualTo("v1");
        assertThat(user.getPrivacyVersion()).isEqualTo("v1");
        assertThat(user.isTermsRequired("v1", "v1")).isFalse();
    }
}
