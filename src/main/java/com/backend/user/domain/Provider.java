package com.backend.user.domain;

import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;

public enum Provider {
    GOOGLE,
    KAKAO;

    public static Provider from(String provider) {
        try {
            return Provider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.INVALID_PROVIDER);
        }
    }
}
