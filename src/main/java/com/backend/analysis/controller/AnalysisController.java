package com.backend.analysis.controller;

import com.backend.analysis.application.AnalysisService;
import com.backend.analysis.dto.request.AnalysisResumeSaveRequest;
import com.backend.analysis.dto.request.AnalysisSatisfactionRequest;
import com.backend.analysis.dto.response.AnalysisDeleteResponse;
import com.backend.analysis.dto.response.AnalysisDetailResponse;
import com.backend.analysis.dto.response.AnalysisPageResponse;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
import com.backend.analysis.dto.response.AnalysisSummaryResponse;
import com.backend.global.response.ApiResponse;
import com.backend.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.backend.global.config.OpenApiConfig.JWT_SECURITY_SCHEME_NAME;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AnalysisPageResponse<AnalysisSummaryResponse>>> getAnalyses(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String companyName
    ) {
        AnalysisPageResponse<AnalysisSummaryResponse> response = analysisService.getAnalyses(
                principal.getUserId(),
                page,
                size,
                companyName
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AnalysisDetailResponse>> createAnalysis(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String jobPostingUrl,
            @RequestParam(required = false) String jobPostingText,
            @RequestPart(name = "jobPostingImage", required = false) MultipartFile jobPostingImage,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "resumePdf", required = false) MultipartFile resumePdf,
            MultipartHttpServletRequest multipartRequest
    ) {
        MultipartFile selectedResumePdf = selectResumePdf(file, resumePdf, multipartRequest);
        AnalysisDetailResponse response = analysisService.createAnalysis(
                principal.getUserId(),
                jobPostingUrl,
                jobPostingText,
                jobPostingImage,
                selectedResumePdf
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private MultipartFile selectResumePdf(
            MultipartFile file,
            MultipartFile resumePdf,
            MultipartHttpServletRequest multipartRequest
    ) {
        if (hasContent(file)) {
            return file;
        }

        if (hasContent(resumePdf)) {
            return resumePdf;
        }

        List<String> fileParts = describeFileParts(multipartRequest);
        if (fileParts.isEmpty()) {
            log.warn("Analysis upload received no multipart file parts");
        } else {
            log.warn("Analysis upload received multipart file parts: {}", fileParts);
        }

        for (MultipartFile multipartFile : multipartRequest.getFileMap().values()) {
            if (hasContent(multipartFile) && isResumePdfCandidate(multipartFile)) {
                return multipartFile;
            }
        }

        if (file != null) {
            return file;
        }

        return resumePdf;
    }

    private boolean hasContent(MultipartFile multipartFile) {
        return multipartFile != null && !multipartFile.isEmpty();
    }

    private boolean isResumePdfCandidate(MultipartFile multipartFile) {
        if ("jobPostingImage".equals(multipartFile.getName())) {
            return false;
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String contentType = multipartFile.getContentType();

        return (originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                || MediaType.APPLICATION_PDF_VALUE.equals(contentType);
    }

    private List<String> describeFileParts(MultipartHttpServletRequest multipartRequest) {
        List<String> fileParts = new ArrayList<>();

        multipartRequest.getFileMap().forEach((partName, multipartFile) ->
                fileParts.add("%s(originalFilename=%s, contentType=%s, size=%d)".formatted(
                        partName,
                        multipartFile.getOriginalFilename(),
                        multipartFile.getContentType(),
                        multipartFile.getSize()
                ))
        );

        return fileParts;
    }

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

    @DeleteMapping("/{analysisResultId}")
    @SecurityRequirement(name = JWT_SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<AnalysisDeleteResponse>> deleteAnalysisResult(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long analysisResultId
    ) {
        AnalysisDeleteResponse response = analysisService.deleteAnalysisResult(
                principal.getUserId(),
                analysisResultId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
