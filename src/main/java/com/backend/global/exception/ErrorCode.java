package com.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "유효하지 않은 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_TOKEN_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "소셜 Access Token 발급에 실패했습니다."),
    OAUTH_USER_INFO_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "소셜 사용자 정보 조회에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "저장된 Refresh Token이 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ALREADY_REGISTERED_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
