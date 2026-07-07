package com.backend.auth.infrastructure;

import com.backend.auth.domain.TokenType;
import com.backend.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(User user) {
        return createToken(user, TokenType.ACCESS, jwtProperties.getAccessTokenExpiration());
    }

    public String createRefreshToken(User user) {
        return createToken(user, TokenType.REFRESH, jwtProperties.getRefreshTokenExpiration());
    }

    private String createToken(User user, TokenType tokenType, long expirationMillis) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("provider", user.getProvider().name())
                .claim("tokenType", tokenType.name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(getClaims(token).getSubject());
    }

    public String getTokenType(String token) {
        return getClaims(token).get("tokenType", String.class);
    }

    public long getRefreshTokenExpiration() {
        return jwtProperties.getRefreshTokenExpiration();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public void validateAccessToken(String token) {
        validateToken(token);

        if (!TokenType.ACCESS.name().equals(getTokenType(token))) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    public void validateRefreshToken(String token) {
        validateToken(token);

        if (!TokenType.REFRESH.name().equals(getTokenType(token))) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateToken(String token) {
        try {
            getClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }
}