package com.backend.analysis.controller;

import com.backend.analysis.dto.AnalysisReRequest;
import com.backend.analysis.dto.AnalysisReResponse;
import com.backend.analysis.service.AnalysisService;
import com.backend.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysesRe")
public class AnalysisReController {

    // 재분석 요청의 전체 흐름은 Service에서 처리
    private final AnalysisService analysisService;

    public AnalysisReController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ApiResponse<AnalysisReResponse> reanalyze(
            @RequestBody AnalysisReRequest request
    ) {
        // 수정된 이력서와 원본 공고를 Service로 전달
        return ApiResponse.success(analysisService.reanalyze(request));
    }
}
