package com.backend.auth.infrastructure;

import com.backend.auth.dto.response.SocialUserInfo;
import com.backend.auth.infrastructure.dto.GoogleUserInfoResponse;
import com.backend.auth.infrastructure.dto.OAuthTokenResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;

    @Override
    public Provider getProvider() {
        return Provider.GOOGLE;
    }

    @Override
    public SocialUserInfo getUserInfo(String authorizationCode) {
        OAuthProperties.OAuth google = oAuthProperties.getGoogle();

        OAuthTokenResponse tokenResponse = requestAccessToken(google, authorizationCode);
        GoogleUserInfoResponse userInfoResponse = requestUserInfo(google, tokenResponse.getAccessToken());

        return SocialUserInfo.builder()
                .provider(Provider.GOOGLE)
                .providerId(userInfoResponse.getSub())
                .email(userInfoResponse.getEmail())
                .name(userInfoResponse.getName())
                .build();
    }

    private OAuthTokenResponse requestAccessToken(
            OAuthProperties.OAuth google,
            String authorizationCode
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", google.getClientId());
        body.add("client_secret", google.getClientSecret());
        body.add("redirect_uri", google.getRedirectUri());
        body.add("code", authorizationCode);

        try {
            ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                    google.getTokenUri(),
                    new HttpEntity<>(body, headers),
                    OAuthTokenResponse.class
            );

            OAuthTokenResponse tokenResponse = response.getBody();

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new CustomException(ErrorCode.OAUTH_SERVER_ERROR);
            }

            return tokenResponse;
        } catch (HttpStatusCodeException e) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }

    private GoogleUserInfoResponse requestUserInfo(
            OAuthProperties.OAuth google,
            String accessToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<GoogleUserInfoResponse> response = restTemplate.exchange(
                    google.getUserInfoUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    GoogleUserInfoResponse.class
            );

            GoogleUserInfoResponse userInfoResponse = response.getBody();

            if (userInfoResponse == null || userInfoResponse.getSub() == null) {
                throw new CustomException(ErrorCode.OAUTH_SERVER_ERROR);
            }

            return userInfoResponse;
        } catch (HttpStatusCodeException e) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.OAUTH_SERVER_ERROR);
        }
    }
}
