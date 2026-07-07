package com.backend.auth.application;

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
import com.backend.auth.dto.response.AuthMeResponse;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final List<OAuthClient> oAuthClients;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse socialLogin(Provider provider, String authorizationCode) {
        SocialUserInfo socialUserInfo = getSocialUserInfo(provider, authorizationCode);

        User user = findOrCreateUser(socialUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);

        refreshTokenRepository.save(
                user.getId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpiration()
        );

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    private SocialUserInfo getSocialUserInfo(Provider provider, String authorizationCode) {
        OAuthClient oAuthClient = oAuthClients.stream()
                .filter(client -> client.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PROVIDER));

        return oAuthClient.getUserInfo(authorizationCode);
    }

    private User findOrCreateUser(SocialUserInfo socialUserInfo) {
        return userRepository.findByProviderAndProviderId(
                        socialUserInfo.getProvider(),
                        socialUserInfo.getProviderId()
                )
                .orElseGet(() -> createNewUser(socialUserInfo));
    }

    private User createNewUser(SocialUserInfo socialUserInfo) {
        if (userRepository.existsByEmail(socialUserInfo.getEmail())) {
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

    @Transactional(readOnly = true)
    public AuthMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return AuthMeResponse.from(user);
    }

    @Transactional
    public void logout(Long userId) {
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
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user);

        refreshTokenRepository.save(
                user.getId(),
                newRefreshToken,
                jwtTokenProvider.getRefreshTokenExpiration()
        );

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }
}