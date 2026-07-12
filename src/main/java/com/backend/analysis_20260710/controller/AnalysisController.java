package com.backend.analysis_20260710.controller;

import com.backend.analysis_20260710.dto.AnalysisResponse;
import com.backend.analysis_20260710.service.AnalysisService;
import com.backend.global.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AnalysisResponse> analyze(
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "url") String jobPostingMode,
            @RequestParam(required = false) String jobPostingUrl,
            @RequestParam(required = false) String jobUrl,
            @RequestParam(required = false) String jobPostingText,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "resumePdf", required = false) MultipartFile resumePdf
    ) {
        MultipartFile selectedResumePdf = file != null ? file : resumePdf;
        String selectedJobUrl = jobPostingUrl != null ? jobPostingUrl : jobUrl;
        AnalysisResponse response = analysisService.analyze(
                userId,
                jobPostingMode,
                selectedJobUrl,
                jobPostingText,
                selectedResumePdf
        );
        return ApiResponse.success(response);
    }
}
