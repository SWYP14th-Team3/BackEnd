package com.backend.auth.infrastructure;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refreshToken:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long userId, String refreshToken, long expirationMillis) {
        String key = createKey(userId);

        stringRedisTemplate.opsForValue().set(
                key,
                refreshToken,
                Duration.ofMillis(expirationMillis)
        );
    }

    public Optional<String> findByUserId(Long userId) {
        return Optional.ofNullable(
                stringRedisTemplate.opsForValue().get(createKey(userId))
        );
    }

    public void deleteByUserId(Long userId) {
        stringRedisTemplate.delete(createKey(userId));
    }

    private String createKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}