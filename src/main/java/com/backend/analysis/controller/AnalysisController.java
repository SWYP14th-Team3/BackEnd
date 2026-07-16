package com.backend.analysis.controller;

import com.backend.analysis.application.AnalysisService;
import com.backend.analysis.dto.request.AnalysisResumeSaveRequest;
import com.backend.analysis.dto.request.AnalysisSatisfactionRequest;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
import com.backend.global.response.ApiResponse;
import com.backend.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.backend.global.config.OpenApiConfig.JWT_SECURITY_SCHEME_NAME;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PatchMapping("/{analysisResultId}/resume")
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AnalysisSaveResponse>> saveResume(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long analysisResultId,
            @Valid @RequestBody AnalysisResumeSaveRequest request
    ) {
        AnalysisSaveResponse response = analysisService.saveResume(
                principal.getUserId(),
                analysisResultId,
                request.getResumeCurrentText()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{analysisResultId}/satisfaction")
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AnalysisSatisfactionResponse>> updateSatisfaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long analysisResultId,
            @Valid @RequestBody AnalysisSatisfactionRequest request
    ) {
        AnalysisSatisfactionResponse response = analysisService.updateSatisfaction(
                principal.getUserId(),
                analysisResultId,
                request.getSatisfaction()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
