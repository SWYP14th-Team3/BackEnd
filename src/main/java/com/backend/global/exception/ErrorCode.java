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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // PDF
    INVALID_PDF_FILE(HttpStatus.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다."),

    GEMINI_API_ERROR(HttpStatus.BAD_GATEWAY, "Gemini API 호출 중 오류가 발생했습니다."),
    JOB_POSTING_CRAWL_ERROR(HttpStatus.BAD_GATEWAY, "채용공고를 불러오는 중 오류가 발생했습니다."),
    GEMINI_RESPONSE_PARSE_ERROR(HttpStatus.BAD_GATEWAY, "Gemini 응답을 해석하는 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
