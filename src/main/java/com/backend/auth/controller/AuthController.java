package com.backend.auth.controller;

import com.backend.auth.application.AuthService;
import com.backend.auth.dto.request.LogoutRequest;
import com.backend.auth.dto.request.SocialLoginRequest;
import com.backend.auth.dto.response.LoginResponse;
import com.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.backend.global.security.UserPrincipal;
import com.backend.user.domain.Provider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.backend.auth.dto.request.TokenReissueRequest;
import com.backend.auth.dto.response.AuthMeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import static com.backend.global.config.OpenApiConfig.JWT_SECURITY_SCHEME_NAME;

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
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AuthMeResponse>> getMe(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AuthMeResponse response = authService.getMe(principal.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(principal.getUserId(), request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success());
    }
}
