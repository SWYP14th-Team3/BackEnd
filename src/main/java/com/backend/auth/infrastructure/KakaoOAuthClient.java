package com.backend.auth.infrastructure;

import com.backend.auth.dto.response.SocialUserInfo;
import com.backend.auth.infrastructure.dto.KakaoUserInfoResponse;
import com.backend.auth.infrastructure.dto.OAuthTokenResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
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

        OAuthTokenResponse tokenResponse =
                requestAccessToken(kakao, authorizationCode);

        KakaoUserInfoResponse userInfoResponse =
                requestUserInfo(kakao, tokenResponse.getAccessToken());

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

        if (StringUtils.hasText(kakao.getClientSecret())) {
            body.add("client_secret", kakao.getClientSecret());
        }

        log.info(
                "Kakao Access Token 요청: redirectUri={}, clientSecretConfigured={}",
                kakao.getRedirectUri(),
                StringUtils.hasText(kakao.getClientSecret())
        );

        try {
            ResponseEntity<OAuthTokenResponse> response =
                    restTemplate.postForEntity(
                            kakao.getTokenUri(),
                            new HttpEntity<>(body, headers),
                            OAuthTokenResponse.class
                    );

            OAuthTokenResponse tokenResponse = response.getBody();

            if (tokenResponse == null
                    || !StringUtils.hasText(tokenResponse.getAccessToken())) {
                log.error(
                        "Kakao Access Token 응답 본문이 올바르지 않습니다. status={}",
                        response.getStatusCode()
                );

                throw new CustomException(
                        ErrorCode.OAUTH_SERVER_ERROR
                );
            }

            return tokenResponse;
        } catch (HttpStatusCodeException e) {
            log.error(
                    "Kakao Access Token 발급 실패: status={}, response={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );

            throw new CustomException(
                    ErrorCode.OAUTH_AUTHENTICATION_FAILED
            );
        } catch (RestClientException e) {
            log.error(
                    "Kakao Access Token 요청 중 통신 오류가 발생했습니다.",
                    e
            );

            throw new CustomException(
                    ErrorCode.OAUTH_SERVER_ERROR
            );
        }
    }

    private KakaoUserInfoResponse requestUserInfo(
            OAuthProperties.OAuth kakao,
            String accessToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<KakaoUserInfoResponse> response =
                    restTemplate.exchange(
                            kakao.getUserInfoUri(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            KakaoUserInfoResponse.class
                    );

            KakaoUserInfoResponse userInfoResponse = response.getBody();

            if (userInfoResponse == null
                    || userInfoResponse.getId() == null) {
                log.error(
                        "Kakao 사용자 정보 응답 본문이 올바르지 않습니다. status={}",
                        response.getStatusCode()
                );

                throw new CustomException(
                        ErrorCode.OAUTH_SERVER_ERROR
                );
            }

            return userInfoResponse;
        } catch (HttpStatusCodeException e) {
            log.error(
                    "Kakao 사용자 정보 조회 실패: status={}, response={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );

            throw new CustomException(
                    ErrorCode.OAUTH_AUTHENTICATION_FAILED
            );
        } catch (RestClientException e) {
            log.error(
                    "Kakao 사용자 정보 요청 중 통신 오류가 발생했습니다.",
                    e
            );

            throw new CustomException(
                    ErrorCode.OAUTH_SERVER_ERROR
            );
        }
    }
}
