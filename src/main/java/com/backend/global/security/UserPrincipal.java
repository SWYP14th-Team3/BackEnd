package com.backend.global.security;

import com.backend.user.domain.Provider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserPrincipal {

    private final Long userId;
    private final String email;
    private final String name;
    private final Provider provider;
}