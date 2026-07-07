package com.backend.auth.infrastructure;

import com.backend.auth.dto.response.SocialUserInfo;
import com.backend.auth.infrastructure.dto.KakaoUserInfoResponse;
import com.backend.auth.infrastructure.dto.OAuthTokenResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements OAuthClient {

    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;

    @Override
    public Provider getProvider() {
        return Provider.KAKAO;
    }

    @Override
    public SocialUserInfo getUserInfo(String authorizationCode) {
        OAuthProperties.OAuth kakao = oAuthProperties.getKakao();

        OAuthTokenResponse tokenResponse = requestAccessToken(kakao, authorizationCode);
        KakaoUserInfoResponse userInfoResponse = requestUserInfo(kakao, tokenResponse.getAccessToken());

        return SocialUserInfo.builder()
                .provider(Provider.KAKAO)
                .providerId(String.valueOf(userInfoResponse.getId()))
                .email(userInfoResponse.getEmail())
                .name(userInfoResponse.getNickname())
                .build();
    }

    private OAuthTokenResponse requestAccessToken(
            OAuthProperties.OAuth kakao,
            String authorizationCode
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", kakao.getClientId());
        body.add("redirect_uri", kakao.getRedirectUri());
        body.add("code", authorizationCode);

        if (kakao.getClientSecret() != null && !kakao.getClientSecret().isBlank()) {
            body.add("client_secret", kakao.getClientSecret());
        }

        try {
            ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                    kakao.getTokenUri(),
                    new HttpEntity<>(body, headers),
                    OAuthTokenResponse.class
            );

            OAuthTokenResponse tokenResponse = response.getBody();

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new CustomException(ErrorCode.OAUTH_TOKEN_REQUEST_FAILED);
            }

            return tokenResponse;
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.OAUTH_TOKEN_REQUEST_FAILED);
        }
    }

    private KakaoUserInfoResponse requestUserInfo(
            OAuthProperties.OAuth kakao,
            String accessToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                    kakao.getUserInfoUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    KakaoUserInfoResponse.class
            );

            KakaoUserInfoResponse userInfoResponse = response.getBody();

            if (userInfoResponse == null || userInfoResponse.getId() == null) {
                throw new CustomException(ErrorCode.OAUTH_USER_INFO_REQUEST_FAILED);
            }

            return userInfoResponse;
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.OAUTH_USER_INFO_REQUEST_FAILED);
        }
    }
}