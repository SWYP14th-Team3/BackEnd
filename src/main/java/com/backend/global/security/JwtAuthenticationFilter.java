package com.backend.global.security;

import com.backend.auth.infrastructure.JwtTokenProvider;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.global.response.ApiResponse;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String accessToken = resolveToken(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            jwtTokenProvider.validateAccessToken(accessToken);

            Long userId = jwtTokenProvider.getUserId(accessToken);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

            UserPrincipal principal = new UserPrincipal(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getProvider()
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (CustomException e) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(request, response, e.getErrorCode());
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        ErrorCode responseErrorCode = responseErrorCode(request, errorCode);
        response.setStatus(responseErrorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(
                        responseErrorCode.getHttpStatus().value(),
                        responseErrorCode.name(),
                        responseErrorMessage(request, responseErrorCode)
                )
        );
    }

    private ErrorCode responseErrorCode(HttpServletRequest request, ErrorCode errorCode) {
        if ((isLogoutRequest(request) || isResumeSaveRequest(request))
                && (errorCode == ErrorCode.INVALID_TOKEN
                || errorCode == ErrorCode.EXPIRED_TOKEN
                || errorCode == ErrorCode.USER_NOT_FOUND)) {
            return ErrorCode.UNAUTHORIZED;
        }

        return errorCode;
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/logout".equals(request.getRequestURI());
    }

    private String responseErrorMessage(HttpServletRequest request, ErrorCode responseErrorCode) {
        if (responseErrorCode == ErrorCode.UNAUTHORIZED && isResumeSaveRequest(request)) {
            return "인증 정보가 유효하지 않거나 만료되었습니다.";
        }

        return responseErrorCode.getMessage();
    }

    private boolean isResumeSaveRequest(HttpServletRequest request) {
        return "PATCH".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/analyses/[^/]+/resume");
    }
}
