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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_PROVIDER(HttpStatus.BAD_REQUEST, "잘못된 provider 값입니다."),
    OAUTH_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다."),
    OAUTH_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "소셜 로그인 처리 중 서버 오류가 발생했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ALREADY_REGISTERED_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),

    // Analysis
    ANALYSIS_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."),
    ANALYSIS_RESULT_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 분석 결과에 접근할 권한이 없습니다."),
    INVALID_ANALYSIS_SATISFACTION(HttpStatus.BAD_REQUEST, "satisfaction 값이 올바르지 않습니다."),
    EMPTY_RESUME_CONTENT(HttpStatus.BAD_REQUEST, "이력서 내용을 입력해주세요."),
    REANALYSIS_RETRY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "재분석 횟수를 모두 사용했어요."),
    COMPANY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 페이지 요청입니다."),
    INVALID_PDF_FILE(HttpStatus.BAD_REQUEST, "PDF만 가능합니다."),
    PDF_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "10MB 이하만 업로드할 수 있습니다."),
    INVALID_JOB_URL(HttpStatus.BAD_REQUEST, "올바른 URL을 입력해주세요."),
    JOB_TEXT_TOO_SHORT(HttpStatus.BAD_REQUEST, "공고 내용은 100자 이상 입력해주세요."),
    JOB_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "공고 내용은 6000자 미만으로 입력해주세요."),
    TOO_MANY_JOB_IMAGES(HttpStatus.BAD_REQUEST, "최대 10장까지 업로드 가능합니다."),
    INVALID_JOB_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "JPG, PNG만 가능합니다."),
    UNREADABLE_PDF_TEXT(HttpStatus.UNPROCESSABLE_CONTENT, "텍스트를 읽을 수 없는 PDF입니다."),
    RESUME_LOAD_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "이력서를 불러오지 못했어요. 파일을 다시 업로드한 뒤 분석을 시도해주세요."),
    JOB_POSTING_LOAD_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "채용공고를 불러오지 못했어요. URL은 그대로 두고 텍스트를 직접 붙여넣거나 공고 캡쳐 이미지를 업로드해주세요."),
    RESUME_AND_JOB_POSTING_LOAD_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "이력서와 채용공고를 불러오지 못했어요. 파일을 다시 업로드하고, 채용공고 내용을 직접 붙여넣어주세요."),
    ANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "분석에 실패했어요. 다시 시도해주세요."),
    REANALYSIS_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "재분석에 실패했어요. 다시 시도해주세요."),
    GEMINI_API_ERROR(HttpStatus.BAD_GATEWAY, "Gemini API 호출 중 오류가 발생했습니다."),
    JOB_POSTING_CRAWL_ERROR(HttpStatus.BAD_GATEWAY, "채용공고를 불러오는 중 오류가 발생했습니다."),
    GEMINI_RESPONSE_PARSE_ERROR(HttpStatus.BAD_GATEWAY, "Gemini 응답을 해석하는 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
