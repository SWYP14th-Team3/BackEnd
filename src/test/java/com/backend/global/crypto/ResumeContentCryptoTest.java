package com.backend.global.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeContentCryptoTest {

    private static final String KEY_PROPERTY = "resume.content.encryption.key";

    @AfterEach
    void tearDown() {
        System.clearProperty(KEY_PROPERTY);
    }

    @Test
    void encryptAndDecryptResumeContent() {
        setTestKey();
        String plainText = "홍길동\n010-1234-5678 | test@example.com\nSpring Boot 경험";

        String encrypted = ResumeContentCrypto.encrypt(plainText);
        String decrypted = ResumeContentCrypto.decryptIfNeeded(encrypted);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(ResumeContentCrypto.isEncrypted(encrypted)).isTrue();
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void encryptUsesRandomIv() {
        setTestKey();
        String plainText = "same resume content";

        String first = ResumeContentCrypto.encrypt(plainText);
        String second = ResumeContentCrypto.encrypt(plainText);

        assertThat(first).isNotEqualTo(second);
        assertThat(ResumeContentCrypto.decryptIfNeeded(first)).isEqualTo(plainText);
        assertThat(ResumeContentCrypto.decryptIfNeeded(second)).isEqualTo(plainText);
    }

    @Test
    void decryptKeepsLegacyPlainText() {
        String legacyPlainText = "legacy resume content";

        String result = ResumeContentCrypto.decryptIfNeeded(legacyPlainText);

        assertThat(result).isEqualTo(legacyPlainText);
    }

    private void setTestKey() {
        byte[] key = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8);
        System.setProperty(KEY_PROPERTY, Base64.getEncoder().encodeToString(key));
    }
}
