package com.backend.auth.dto.response;

import com.backend.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgreementResponse {

    private Long userId;
    private LocalDateTime termsAgreedAt;
    private LocalDateTime privacyAgreedAt;
    private String termsVersion;
    private String privacyVersion;

    public static AgreementResponse from(User user) {
        return AgreementResponse.builder()
                .userId(user.getId())
                .termsAgreedAt(user.getTermsAgreedAt())
                .privacyAgreedAt(user.getPrivacyAgreedAt())
                .termsVersion(user.getTermsVersion())
                .privacyVersion(user.getPrivacyVersion())
                .build();
    }
}
