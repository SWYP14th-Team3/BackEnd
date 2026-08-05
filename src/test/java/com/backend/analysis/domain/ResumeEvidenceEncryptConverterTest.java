package com.backend.analysis.domain;

import com.backend.global.crypto.ResumeContentCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeEvidenceEncryptConverterTest {

    private static final String KEY_PROPERTY = "resume.content.encryption.key";

    private final ResumeEvidenceEncryptConverter converter = new ResumeEvidenceEncryptConverter();

    @AfterEach
    void tearDown() {
        System.clearProperty(KEY_PROPERTY);
    }

    @Test
    void encryptAndDecryptResumeEvidence() {
        setTestKey();
        String resumeEvidence = "Spring Boot 프로젝트에서 인증 API를 구현했습니다.";

        String encrypted = converter.convertToDatabaseColumn(resumeEvidence);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotEqualTo(resumeEvidence);
        assertThat(ResumeContentCrypto.isEncrypted(encrypted)).isTrue();
        assertThat(decrypted).isEqualTo(resumeEvidence);
    }

    @Test
    void keepLegacyPlainResumeEvidence() {
        String legacyPlainText = "기존 평문 근거";

        String result = converter.convertToEntityAttribute(legacyPlainText);

        assertThat(result).isEqualTo(legacyPlainText);
    }

    private void setTestKey() {
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        System.setProperty(KEY_PROPERTY, Base64.getEncoder().encodeToString(key));
    }
}
