package com.backend.auth.application;

import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.analysis.infrastructure.JobDescriptionRepository;
import com.backend.analysis.infrastructure.JobPostingImageRepository;
import com.backend.analysis.infrastructure.JobRequirementRepository;
import com.backend.analysis.infrastructure.RequirementEvaluationRepository;
import com.backend.analysis.infrastructure.UserResumeRepository;
import com.backend.auth.dto.response.AgreementResponse;
import com.backend.auth.dto.response.AuthMeResponse;
import com.backend.auth.dto.response.LoginResponse;
import com.backend.auth.dto.response.SocialUserInfo;
import com.backend.auth.infrastructure.JwtTokenProvider;
import com.backend.auth.infrastructure.OAuthClient;
import com.backend.auth.infrastructure.RefreshTokenRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final OAuthClient oAuthClient = mock(OAuthClient.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AuthService authService = new AuthService(
            List.of(oAuthClient),
            userRepository,
            jwtTokenProvider,
            refreshTokenRepository,
            mock(RequirementEvaluationRepository.class),
            mock(JobRequirementRepository.class),
            mock(AnalysisResultRepository.class),
            mock(UserResumeRepository.class),
            mock(JobPostingImageRepository.class),
            mock(JobDescriptionRepository.class)
    );

    @Test
    @DisplayName("기존 소셜 사용자는 신규 가입이 아니며 약관 상태를 함께 반환한다")
    void socialLoginReturnsExistingUser() {
        User user = User.createSocialUser(
                "user@gmail.com",
                Provider.GOOGLE,
                "google-provider-id",
                "강인성"
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "privacyAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "termsVersion", "v1");
        ReflectionTestUtils.setField(user, "privacyVersion", "v1");

        stubGoogleOAuthUser("google-provider-id", "user@gmail.com");
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-provider-id"))
                .thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration()).thenReturn(3600000L);

        LoginResponse response = authService.socialLogin(Provider.GOOGLE, "authorization-code");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getIsNewUser()).isFalse();
        assertThat(response.getTermsRequired()).isFalse();
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getProvider()).isEqualTo(Provider.GOOGLE);
        verify(refreshTokenRepository).save(1L, "refresh-token", 3600000L);
    }

    @Test
    @DisplayName("provider/providerId가 다르면 이메일이 같아도 신규 소셜 사용자로 생성한다")
    void socialLoginCreatesNewUserByProviderAndProviderIdEvenWhenEmailExists() {
        stubGoogleOAuthUser("google-provider-id", "user@gmail.com");
        when(userRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-provider-id"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("user@gmail.com")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedUser, "id", 2L);
            return savedUser;
        });
        when(jwtTokenProvider.createAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshTokenExpiration()).thenReturn(3600000L);

        LoginResponse response = authService.socialLogin(Provider.GOOGLE, "authorization-code");

        assertThat(response.getIsNewUser()).isTrue();
        assertThat(response.getTermsRequired()).isTrue();
        assertThat(response.getUser().getId()).isEqualTo(2L);
        assertThat(response.getUser().getEmail()).isEqualTo("user@gmail.com");
        assertThat(response.getUser().getProvider()).isEqualTo(Provider.GOOGLE);
        verify(userRepository, never()).existsByEmail(anyString());
        verify(refreshTokenRepository).save(2L, "refresh-token", 3600000L);
    }

    @Test
    @DisplayName("로그아웃은 access token 사용자와 refresh token 사용자가 같으면 저장된 토큰을 삭제한다")
    void logoutDeletesSavedRefreshToken() {
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of("refresh-token"));

        authService.logout(1L, "refresh-token");

        verify(jwtTokenProvider).validateRefreshToken("refresh-token");
        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    @DisplayName("로그아웃은 refresh token 사용자가 access token 사용자와 다르면 거부한다")
    void logoutRejectsRefreshTokenOwnedByDifferentUser() {
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(2L);

        assertThatThrownBy(() -> authService.logout(1L, "refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Refresh Token이 유효하면 Access Token만 재발급한다")
    void reissueReturnsNewAccessTokenOnly() {
        User user = User.createSocialUser(
                "user@gmail.com",
                Provider.GOOGLE,
                "google-provider-id",
                "강인성"
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of("refresh-token"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access-token");

        LoginResponse response = authService.reissue("refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getIsNewUser()).isNull();
        assertThat(response.getTermsRequired()).isNull();
        assertThat(response.getUser()).isNull();
        verify(jwtTokenProvider).validateRefreshToken("refresh-token");
    }

    @Test
    @DisplayName("저장된 Refresh Token이 없으면 재발급을 거부한다")
    void reissueRejectsMissingSavedRefreshToken() {
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue("refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("저장된 Refresh Token과 요청값이 다르면 재발급을 거부한다")
    void reissueRejectsDifferentRefreshToken() {
        when(jwtTokenProvider.getUserId("refresh-token")).thenReturn(1L);
        when(refreshTokenRepository.findByUserId(1L)).thenReturn(Optional.of("saved-refresh-token"));

        assertThatThrownBy(() -> authService.reissue("refresh-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그인 사용자 정보는 사용자 기본 정보와 약관 필요 여부를 반환한다")
    void getMeReturnsLoginUserInfo() {
        User user = User.createSocialUser(
                "user@gmail.com",
                Provider.GOOGLE,
                "google-provider-id",
                "강인성"
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "termsAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "privacyAgreedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "termsVersion", "v1");
        ReflectionTestUtils.setField(user, "privacyVersion", "v1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AuthMeResponse response = authService.getMe(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("user@gmail.com");
        assertThat(response.getName()).isEqualTo("강인성");
        assertThat(response.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(response.getTermsRequired()).isFalse();
    }

    @Test
    @DisplayName("로그인 사용자 정보 조회 시 사용자가 없으면 거부한다")
    void getMeRejectsMissingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getMe(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("약관 동의 시 동의 시각과 현재 약관 버전을 저장한다")
    void agreeTermsStoresAgreementTimesAndVersions() {
        User user = User.createSocialUser(
                "user@gmail.com",
                Provider.GOOGLE,
                "google-provider-id",
                "강인성"
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AgreementResponse response = authService.agreeTerms(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getTermsAgreedAt()).isNotNull();
        assertThat(response.getPrivacyAgreedAt()).isNotNull();
        assertThat(response.getTermsAgreedAt()).isEqualTo(response.getPrivacyAgreedAt());
        assertThat(response.getTermsVersion()).isEqualTo("v1");
        assertThat(response.getPrivacyVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("약관 동의 시 사용자가 없으면 거부한다")
    void agreeTermsRejectsMissingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.agreeTerms(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private void stubGoogleOAuthUser(String providerId, String email) {
        when(oAuthClient.getProvider()).thenReturn(Provider.GOOGLE);
        when(oAuthClient.getUserInfo("authorization-code")).thenReturn(SocialUserInfo.builder()
                .provider(Provider.GOOGLE)
                .providerId(providerId)
                .email(email)
                .name("강인성")
                .build());
    }
}
