package com.backend.analysis.controller;

import com.backend.analysis.dto.AnalysisResponse;
import com.backend.analysis.service.AnalysisService;
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

    // 분석 요청의 전체 흐름은 Service에서 처리
    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AnalysisResponse> analyze(
            @RequestParam Long userId,
            @RequestParam(required = false) String jobPostingUrl,
            @RequestParam(required = false) String jobPostingText,
            @RequestPart(name = "jobPostingImage", required = false) MultipartFile jobPostingImage,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestPart(name = "resumePdf", required = false) MultipartFile resumePdf
    ) {
        // 프론트에서 file 또는 resumePdf 이름으로 보낸 PDF를 모두 허용
        MultipartFile selectedResumePdf = file != null ? file : resumePdf;

        // 이력서 PDF, 채용공고 URL/텍스트/이미지를 Service로 전달
        AnalysisResponse response = analysisService.analyze(
                userId,
                jobPostingUrl,
                jobPostingText,
                jobPostingImage,
                selectedResumePdf
        );

        return ApiResponse.success(response);
    }
}
