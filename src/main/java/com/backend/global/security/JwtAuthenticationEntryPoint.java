package com.backend.global.security;

import com.backend.global.exception.ErrorCode;
import com.backend.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(errorCode.getHttpStatus().value(), errorCode.name(), errorMessage(request, errorCode))
        );
    }

    private String errorMessage(HttpServletRequest request, ErrorCode errorCode) {
        if (isResumeSaveRequest(request)) {
            return "인증 정보가 유효하지 않거나 만료되었습니다.";
        }

        return errorCode.getMessage();
    }

    private boolean isResumeSaveRequest(HttpServletRequest request) {
        return "PATCH".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+/resume");
    }
}
