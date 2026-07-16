package com.backend.auth.infrastructure;

import com.backend.auth.dto.response.SocialUserInfo;
import com.backend.user.domain.Provider;

public interface OAuthClient {

    Provider getProvider();

    SocialUserInfo getUserInfo(String authorizationCode);
}
