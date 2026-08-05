package com.backend.global.crypto;

import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class ResumeContentCrypto {

    private static final String ENCRYPTED_PREFIX = "enc:aes-gcm:v1:";
    private static final String KEY_PROPERTY = "resume.content.encryption.key";
    private static final String KEY_ENV = "RESUME_CONTENT_ENCRYPTION_KEY";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ResumeContentCrypto() {
    }

    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
            payload.put(iv);
            payload.put(encrypted);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static String decryptIfNeeded(String storedValue) {
        if (storedValue == null || !isEncrypted(storedValue)) {
            return storedValue;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted resume content payload");
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private static SecretKeySpec secretKey() {
        String encodedKey = System.getProperty(KEY_PROPERTY);
        if (encodedKey == null || encodedKey.isBlank()) {
            encodedKey = System.getenv(KEY_ENV);
        }

        if (encodedKey == null || encodedKey.isBlank()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != KEY_BYTES) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return new SecretKeySpec(key, ALGORITHM);
    }
}
