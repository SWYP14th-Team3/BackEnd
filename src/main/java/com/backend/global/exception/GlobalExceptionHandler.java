package com.backend.global.exception;

import com.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e, HttpServletRequest request) {
        // 서비스에서 던진 프로젝트 전용 예외를 공통 응답으로 변환
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: {}", errorCode.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(
                        errorCode.getHttpStatus().value(),
                        errorCode.name(),
                        customExceptionMessage(errorCode, request)
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        // DTO 검증 실패 메시지를 하나의 문자열로 합쳐 반환
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("ValidationException: {}", message);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, ErrorCode.INVALID_INPUT_VALUE.name(), message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String parameterName = e.getName();
        ErrorCode errorCode = ("page".equals(parameterName) || "size".equals(parameterName))
                ? ErrorCode.INVALID_PAGE_REQUEST
                : ErrorCode.INVALID_INPUT_VALUE;

        log.warn("TypeMismatchException: parameter={}, value={}", parameterName, e.getValue());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(
                        errorCode.getHttpStatus().value(),
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        // 예상하지 못한 예외는 서버 오류 응답으로 변환
        log.error("UnhandledException: {}", e.getMessage(), e);
        ErrorCode errorCode = isSocialLoginRequest(request)
                ? ErrorCode.OAUTH_SERVER_ERROR
                : ErrorCode.INTERNAL_SERVER_ERROR;
        String message = isSocialLoginRequest(request)
                ? ErrorCode.OAUTH_SERVER_ERROR.getMessage()
                : isLogoutRequest(request)
                ? "로그아웃 처리 중 서버 오류가 발생했습니다."
                : isAnalysisListRequest(request)
                ? "분석 결과 목록 조회 중 서버 오류가 발생했습니다."
                : isAnalysisCreateRequest(request)
                ? "분석 생성 중 서버 오류가 발생했습니다."
                : isResumeSaveRequest(request)
                ? "이력서 자동저장 중 서버 오류가 발생했습니다."
                : isFinalSaveRequest(request)
                ? "분석 결과 저장 중 서버 오류가 발생했습니다."
                : isReanalysisRequest(request)
                ? "이력서 재분석 중 서버 오류가 발생했습니다."
                : isAnalysisDeleteRequest(request)
                ? "분석 결과 삭제 중 서버 오류가 발생했습니다."
                : ErrorCode.INTERNAL_SERVER_ERROR.getMessage();
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(
                        500,
                        errorCode.name(),
                        message
                ));
    }

    private boolean isAnalysisCreateRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/analyses".equals(request.getRequestURI());
    }

    private boolean isAnalysisListRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && "/api/analyses".equals(request.getRequestURI())
                && request.getParameter("companyName") == null;
    }

    private String customExceptionMessage(ErrorCode errorCode, HttpServletRequest request) {
        if (errorCode == ErrorCode.ANALYSIS_RESULT_FORBIDDEN
                && (isResumeSaveRequest(request) || isReanalysisRequest(request))) {
            return "해당 분석 결과를 수정할 권한이 없습니다.";
        }
        if (errorCode == ErrorCode.ANALYSIS_RESULT_FORBIDDEN && isFinalSaveRequest(request)) {
            return "해당 분석 결과를 저장할 권한이 없습니다.";
        }
        if (errorCode == ErrorCode.ANALYSIS_RESULT_FORBIDDEN && isAnalysisDeleteRequest(request)) {
            return "해당 분석 결과를 삭제할 권한이 없습니다.";
        }
        if (errorCode == ErrorCode.INVALID_REFRESH_TOKEN && isLogoutRequest(request)) {
            return "Refresh Token이 유효하지 않습니다.";
        }

        return errorCode.getMessage();
    }

    private boolean isReanalysisRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+/reanalyze");
    }

    private boolean isResumeSaveRequest(HttpServletRequest request) {
        return "PATCH".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+/resume");
    }

    private boolean isFinalSaveRequest(HttpServletRequest request) {
        return "PATCH".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+/save");
    }

    private boolean isAnalysisDeleteRequest(HttpServletRequest request) {
        return "DELETE".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+");
    }

    private boolean isSocialLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/auth/oauth/[^/]+/login");
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/logout".equals(request.getRequestURI());
    }
}
