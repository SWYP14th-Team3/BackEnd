package com.backend.auth.controller;

import com.backend.auth.application.AuthService;
import com.backend.auth.dto.request.SocialLoginRequest;
import com.backend.auth.dto.response.LoginResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.global.response.ApiResponse;
import com.backend.global.security.UserPrincipal;
import com.backend.user.domain.Provider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.backend.auth.dto.request.TokenReissueRequest;
import com.backend.auth.dto.response.AuthMeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RequestMapping("/api/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/oauth/{provider}/login")
    public ResponseEntity<ApiResponse<LoginResponse>> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request
    ) {
        Provider socialProvider = Provider.from(provider);

        LoginResponse response = authService.socialLogin(
                socialProvider,
                request.getAuthorizationCode()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }



    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @Valid @RequestBody TokenReissueRequest request
    ) {
        LoginResponse response = authService.reissue(request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> getMe(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AuthMeResponse response = authService.getMe(principal.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        authService.logout(principal.getUserId());

        return ResponseEntity.ok(ApiResponse.success());
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        return authorizationHeader.substring(7);
    }
}