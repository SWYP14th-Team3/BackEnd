package com.backend.analysis.controller;

import com.backend.analysis.application.AnalysisService;
import com.backend.analysis.domain.JobInputType;
import com.backend.global.security.UserPrincipal;
import com.backend.user.domain.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock
    private AnalysisService analysisService;

    @InjectMocks
    private AnalysisController analysisController;

    @Test
    @DisplayName("URL 입력에서 jobPostingRaw 파라미터를 직접 입력 공고 텍스트로 전달한다")
    void createAnalysisUsesJobPostingRawWhenJobTextIsEmpty() {
        UserPrincipal principal = new UserPrincipal(1L, "user@example.com", "테스터", Provider.GOOGLE);
        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4".getBytes()
        );
        String jobUrl = "https://www.jobkorea.co.kr/Recruit/GI_Read/49582513";
        String jobPostingRaw = "사용자가 직접 입력한 채용공고 원문";

        analysisController.createAnalysis(
                principal,
                resumeFile,
                List.of(),
                JobInputType.URL,
                jobUrl,
                null,
                jobPostingRaw
        );

        verify(analysisService).createAnalysis(
                eq(1L),
                eq(JobInputType.URL),
                eq(jobUrl),
                eq(jobPostingRaw),
                eq(resumeFile),
                any()
        );
    }
}
