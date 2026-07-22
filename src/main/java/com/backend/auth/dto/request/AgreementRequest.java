package com.backend.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgreementRequest {

    @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
    private boolean termsAgreed;

    @AssertTrue(message = "개인정보 수집·이용 및 AI 분석을 위한 제3자 제공에 동의해야 합니다.")
    private boolean privacyAgreed;
}
