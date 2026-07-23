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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CURRENT_TERMS_VERSION = "v1";
    private static final String CURRENT_PRIVACY_VERSION = "v1";

    private final List<OAuthClient> oAuthClients;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RequirementEvaluationRepository requirementEvaluationRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserResumeRepository userResumeRepository;
    private final JobPostingImageRepository jobPostingImageRepository;
    private final JobDescriptionRepository jobDescriptionRepository;

    @Transactional
    public LoginResponse socialLogin(Provider provider, String authorizationCode) {
        SocialUserInfo socialUserInfo = getSocialUserInfo(provider, authorizationCode);

        LoginUser loginUser = findOrCreateUser(socialUserInfo);
        User user = loginUser.user();

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        refreshTokenRepository.save(
                user.getId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpiration()
        );

        return LoginResponse.of(
                accessToken,
                refreshToken,
                loginUser.isNewUser(),
                user.isTermsRequired(CURRENT_TERMS_VERSION, CURRENT_PRIVACY_VERSION),
                user
        );
    }

    private SocialUserInfo getSocialUserInfo(Provider provider, String authorizationCode) {
        OAuthClient oAuthClient = oAuthClients.stream()
                .filter(client -> client.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROVIDER));

        return oAuthClient.getUserInfo(authorizationCode);
    }

    private LoginUser findOrCreateUser(SocialUserInfo socialUserInfo) {
        return userRepository.findByProviderAndProviderId(
                        socialUserInfo.getProvider(),
                        socialUserInfo.getProviderId()
                )
                .map(user -> new LoginUser(user, false))
                .orElseGet(() -> new LoginUser(createNewUser(socialUserInfo), true));
    }

    private User createNewUser(SocialUserInfo socialUserInfo) {
        if (StringUtils.hasText(socialUserInfo.getEmail())
                && userRepository.existsByEmail(socialUserInfo.getEmail())) {
            throw new CustomException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        User user = User.createSocialUser(
                socialUserInfo.getEmail(),
                socialUserInfo.getProvider(),
                socialUserInfo.getProviderId(),
                socialUserInfo.getName()
        );

        return userRepository.save(user);
    }

    private record LoginUser(
            User user,
            boolean isNewUser
    ) {
    }

    @Transactional(readOnly = true)
    public AuthMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return AuthMeResponse.from(
                user,
                user.isTermsRequired(CURRENT_TERMS_VERSION, CURRENT_PRIVACY_VERSION)
        );
    }

    @Transactional
    public void deleteMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        refreshTokenRepository.deleteByUserId(userId);
        requirementEvaluationRepository.deleteAllByUserId(userId);
        jobRequirementRepository.deleteAllByUserId(userId);
        analysisResultRepository.deleteAllByUserId(userId);
        userResumeRepository.deleteAllByUserId(userId);
        jobPostingImageRepository.deleteAllByUserId(userId);
        jobDescriptionRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
    }

    @Transactional
    public AgreementResponse agreeTerms(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.agreeTerms(
                LocalDateTime.now(),
                CURRENT_TERMS_VERSION,
                CURRENT_PRIVACY_VERSION
        );

        return AgreementResponse.from(user);
    }

    @Transactional
    public void logout(Long userId, String refreshToken) {
        jwtTokenProvider.validateRefreshToken(refreshToken);

        Long refreshTokenUserId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenUserId.equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String savedRefreshToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!savedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public LoginResponse reissue(String refreshToken) {
        jwtTokenProvider.validateRefreshToken(refreshToken);

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        String savedRefreshToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!savedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(user);

        return LoginResponse.accessToken(newAccessToken);
    }
}
