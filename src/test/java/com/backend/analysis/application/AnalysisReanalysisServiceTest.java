package com.backend.analysis.application;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiCardContentResult;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.dto.response.ReanalysisResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.analysis.infrastructure.JobRequirementRepository;
import com.backend.analysis.infrastructure.RequirementEvaluationRepository;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisReanalysisServiceTest {

    private final GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
    private final AnalysisResultRepository analysisResultRepository = mock(AnalysisResultRepository.class);
    private final JobRequirementRepository jobRequirementRepository = mock(JobRequirementRepository.class);
    private final RequirementEvaluationRepository requirementEvaluationRepository = mock(RequirementEvaluationRepository.class);
    private final AnalysisService analysisService = new AnalysisService(
            null,
            geminiAnalysisClient,
            null,
            null,
            null,
            null,
            analysisResultRepository,
            jobRequirementRepository,
            requirementEvaluationRepository
    );

    @Test
    @DisplayName("재분석 요청 이력서가 저장된 이력서와 같으면 Gemini 호출과 재분석 횟수 증가를 건너뛴다")
    void reanalyzeSkipsGeminiWhenResumeContentIsUnchanged() {
        AnalysisResult analysisResult = createAnalysisResult();
        JobRequirement requirement = createRequirement(analysisResult);
        RequirementEvaluation evaluation = createEvaluation(requirement);

        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));
        when(jobRequirementRepository.findAllByAnalysisResultOrderByInputOrderAscIdAsc(analysisResult))
                .thenReturn(List.of(requirement));
        when(requirementEvaluationRepository.findByJobRequirement(requirement))
                .thenReturn(Optional.of(evaluation));

        ReanalysisResponse response = analysisService.reanalyze(
                1L,
                1L,
                "  기존 이력서 텍스트\r\n"
        );

        assertThat(response.getPreviousOverallLevel()).isEqualTo(OverallLevel.MEDIUM);
        assertThat(response.getPreviousRedCount()).isEqualTo(1);
        assertThat(response.getPreviousYellowCount()).isEqualTo(2);
        assertThat(response.getPreviousGreenCount()).isEqualTo(3);
        assertThat(response.getOverallLevel()).isEqualTo(OverallLevel.MEDIUM);
        assertThat(response.getRedCount()).isEqualTo(1);
        assertThat(response.getYellowCount()).isEqualTo(2);
        assertThat(response.getGreenCount()).isEqualTo(3);
        assertThat(response.getRetryCount()).isZero();
        assertThat(response.getRemainingRetryCount()).isEqualTo(5);
        assertThat(response.getLastReanalyzedAt()).isNull();
        assertThat(response.getResumeCurrentText()).isEqualTo("기존 이력서 텍스트\n");
        assertThat(response.getRequirements()).hasSize(1);

        verifyNoInteractions(geminiAnalysisClient);
        verify(analysisResultRepository, never()).flush();
    }

    @Test
    @DisplayName("마지막 분석 이후 자동저장된 이력서는 저장된 내용과 요청 내용이 같아도 재분석한다")
    void reanalyzeRunsWhenResumeWasSavedAfterLastAnalysis() {
        AnalysisResult analysisResult = createAnalysisResult();
        JobRequirement requirement = createRequirement(analysisResult);
        RequirementEvaluation evaluation = createEvaluation(requirement);
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 7, 26, 18, 0);
        LocalDateTime autosavedAt = LocalDateTime.of(2026, 7, 26, 18, 5);
        ReflectionTestUtils.setField(analysisResult, "createdAt", analyzedAt);
        analysisResult.getUserResume().updateResumeContent("수정된 이력서 텍스트", autosavedAt);

        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));
        when(jobRequirementRepository.findAllByAnalysisResultOrderByInputOrderAscIdAsc(analysisResult))
                .thenReturn(List.of(requirement));
        when(requirementEvaluationRepository.findByJobRequirement(requirement))
                .thenReturn(Optional.of(evaluation));
        when(geminiAnalysisClient.reanalyze(anyString()))
                .thenReturn(new GeminiAnalysisResponse(
                        true,
                        null,
                        null,
                        null,
                        null,
                        List.of(new GeminiRequirementResult(
                                "r2",
                                "Spring Boot 개발 경험",
                                "필수",
                                "Spring Boot 기반 백엔드 개발 경험 보유자",
                                "수정된 이력서 텍스트",
                                "수정된 이력서에서 Spring Boot 경험이 확인됩니다.",
                                null,
                                null,
                                null,
                                "green",
                                null,
                                null,
                                null,
                                null
                        ))
                ));
        when(geminiAnalysisClient.createCardContents(anyString()))
                .thenReturn(List.of(new GeminiCardContentResult(
                        "r2",
                        "green",
                        "Spring Boot 경험이 확인됐어요",
                        "Spring Boot 경험이 명확히 확인됩니다.",
                        null
                )));

        ReanalysisResponse response = analysisService.reanalyze(
                1L,
                1L,
                "수정된 이력서 텍스트"
        );

        assertThat(response.getRetryCount()).isEqualTo(1);
        assertThat(response.getRedCount()).isZero();
        assertThat(response.getYellowCount()).isZero();
        assertThat(response.getGreenCount()).isEqualTo(1);
        assertThat(response.getResumeCurrentText()).isEqualTo("수정된 이력서 텍스트");

        verify(geminiAnalysisClient).reanalyze(anyString());
        verify(analysisResultRepository).flush();
    }

    private AnalysisResult createAnalysisResult() {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("기존 이력서 텍스트\n")
                .resumeFileName("resume.pdf")
                .resumeFileSize(480029L)
                .build();

        JobDescription jobDescription = JobDescription.builder()
                .user(user)
                .companyName("카카오")
                .positionTitle("백엔드 개발자")
                .jobPlatform("company")
                .jdOriginalText("공고 원문")
                .jdSummaryText("공고 요약")
                .build();

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(user)
                .userResume(userResume)
                .jobDescription(jobDescription)
                .overallLevel(OverallLevel.MEDIUM)
                .redCount(1)
                .yellowCount(2)
                .greenCount(3)
                .build();
        ReflectionTestUtils.setField(analysisResult, "id", 1L);

        return analysisResult;
    }

    private JobRequirement createRequirement(AnalysisResult analysisResult) {
        JobRequirement requirement = JobRequirement.builder()
                .analysisResult(analysisResult)
                .requirementType(RequirementType.REQUIRED)
                .category(RequirementCategory.QUALIFICATION)
                .title("Spring Boot 개발 경험")
                .description("Spring Boot 기반 백엔드 개발 경험")
                .jdEvidence("Spring Boot 기반 백엔드 개발 경험 보유자")
                .inputOrder(1)
                .build();
        ReflectionTestUtils.setField(requirement, "id", 1L);

        return requirement;
    }

    private RequirementEvaluation createEvaluation(JobRequirement requirement) {
        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.NEEDS_IMPROVEMENT)
                .displayTitle("Spring Boot 경험을 더 구체화하세요")
                .resumeEvidence("Spring Boot 프로젝트 경험")
                .judgeReason("구체적인 역할 설명이 부족합니다.")
                .feedback("구현 기능을 더 명확히 작성해야 합니다.")
                .revisionSuggestion("Spring Boot로 JWT 인증 API를 구현한 경험을 구체적으로 작성해보세요.")
                .effectScore(4)
                .effortScore(2)
                .priorityScore(BigDecimal.valueOf(8.0))
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(evaluation, "id", 1L);

        return evaluation;
    }
}
