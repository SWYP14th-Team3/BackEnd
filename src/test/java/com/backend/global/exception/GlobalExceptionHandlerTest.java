package com.backend.global.exception;

import com.backend.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("분석 목록 조회 중 예상하지 못한 예외는 명세의 500 메시지로 응답한다")
    void analysisListUnhandledExceptionUsesRouteSpecificMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analyses");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("분석 결과 목록 조회 중 서버 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("회사명 검색 중 예상하지 못한 예외는 명세의 기본 500 메시지로 응답한다")
    void analysisSearchUnhandledExceptionUsesDefaultServerErrorMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/analyses");
        request.addParameter("companyName", "카카오");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("서버 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("이력서 자동저장 권한 오류는 수정 권한 메시지로 응답한다")
    void resumeSaveForbiddenUsesEditPermissionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/resume");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCustomException(
                new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.ANALYSIS_RESULT_FORBIDDEN.name());
        assertThat(response.getBody().getMessage()).isEqualTo("해당 분석 결과를 수정할 권한이 없습니다.");
    }

    @Test
    @DisplayName("이력서 자동저장 중 예상하지 못한 예외는 명세의 500 메시지로 응답한다")
    void resumeSaveUnhandledExceptionUsesRouteSpecificMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/resume");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("이력서 자동저장 중 서버 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("분석 결과 최종 저장 권한 오류는 저장 권한 메시지로 응답한다")
    void finalSaveForbiddenUsesSavePermissionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/save");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCustomException(
                new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.ANALYSIS_RESULT_FORBIDDEN.name());
        assertThat(response.getBody().getMessage()).isEqualTo("해당 분석 결과를 저장할 권한이 없습니다.");
    }

    @Test
    @DisplayName("분석 결과 최종 저장 중 예상하지 못한 예외는 명세의 500 메시지로 응답한다")
    void finalSaveUnhandledExceptionUsesRouteSpecificMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/save");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("분석 결과 저장 중 서버 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("분석 결과 삭제 권한 오류는 삭제 권한 메시지로 응답한다")
    void analysisDeleteForbiddenUsesDeletePermissionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/analyses/1");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCustomException(
                new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.ANALYSIS_RESULT_FORBIDDEN.name());
        assertThat(response.getBody().getMessage()).isEqualTo("해당 분석 결과를 삭제할 권한이 없습니다.");
    }

    @Test
    @DisplayName("분석 결과 삭제 중 예상하지 못한 예외는 명세의 500 메시지로 응답한다")
    void analysisDeleteUnhandledExceptionUsesRouteSpecificMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/analyses/1");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("분석 결과 삭제 중 서버 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("분석 만족도 저장 권한 오류는 접근 권한 메시지로 응답한다")
    void analysisSatisfactionForbiddenUsesAccessPermissionMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/satisfaction");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleCustomException(
                new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.ANALYSIS_RESULT_FORBIDDEN.name());
        assertThat(response.getBody().getMessage()).isEqualTo("해당 분석 결과에 접근할 권한이 없습니다.");
    }

    @Test
    @DisplayName("분석 만족도 저장 중 예상하지 못한 예외는 기본 500 메시지로 응답한다")
    void analysisSatisfactionUnhandledExceptionUsesDefaultServerErrorMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/analyses/1/satisfaction");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleException(
                new RuntimeException("database failure"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getErrorType()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.getBody().getMessage()).isEqualTo("서버 오류가 발생했습니다.");
    }
}
