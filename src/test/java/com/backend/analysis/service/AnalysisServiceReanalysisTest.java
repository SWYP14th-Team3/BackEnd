package com.backend.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.dto.AnalysisReRequest;
import com.backend.analysis.dto.AnalysisReResponse;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiAnalysisSummary;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.repository.AnalysisResultRepository;
import com.backend.analysis.repository.JobDescriptionRepository;
import com.backend.analysis.repository.JobRequirementRepository;
import com.backend.analysis.repository.RequirementEvaluationRepository;
import com.backend.analysis.repository.UserRepository;
import com.backend.analysis.repository.UserResumeRepository;
import com.backend.global.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class AnalysisServiceReanalysisTest {

    // 외부 Gemini API 호출은 Mock으로 대체
    private GeminiAnalysisClient geminiAnalysisClient;
    // analysis_result 조회를 위한 Mock Repository
    private AnalysisResultRepository analysisResultRepository;
    // job_requirement 조회를 위한 Mock Repository
    private JobRequirementRepository jobRequirementRepository;
    // requirement_evaluation 조회를 위한 Mock Repository
    private RequirementEvaluationRepository requirementEvaluationRepository;
    // 재분석 로직을 검증할 Service
    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        // 테스트마다 독립적인 Mock 객체 생성
        geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        analysisResultRepository = mock(AnalysisResultRepository.class);
        jobRequirementRepository = mock(JobRequirementRepository.class);
        requirementEvaluationRepository = mock(RequirementEvaluationRepository.class);

        analysisService = new AnalysisService(
                mock(JobPostingCrawler.class),
                geminiAnalysisClient,
                mock(UserRepository.class),
                mock(UserResumeRepository.class),
                mock(JobDescriptionRepository.class),
                analysisResultRepository,
                jobRequirementRepository,
                requirementEvaluationRepository,
                new ObjectMapper()
        );
    }

    @Test
    void reanalyze_updatesExistingAnalysisResultRequirementsAndEvaluations() {
        // 기존 분석 결과와 요건/평가 row 준비
        AnalysisResult result = analysisResult();
        JobRequirement firstRequirement = jobRequirement(10L, "REQUIRED", "Java 경험");
        JobRequirement secondRequirement = jobRequirement(11L, "PREFERRED", "CI/CD 경험");
        RequirementEvaluation firstEvaluation = requirementEvaluation(20L, 10L, "MISSING");
        RequirementEvaluation secondEvaluation = requirementEvaluation(21L, 11L, "MISSING");

        when(analysisResultRepository.findById(1L)).thenReturn(Optional.of(result));
        when(jobRequirementRepository.findByAnalysisResultIdOrderByIdAsc(1L))
                .thenReturn(List.of(firstRequirement, secondRequirement));
        when(requirementEvaluationRepository.findByAnalysisResultIdOrderByRequirementIdAsc(1L))
                .thenReturn(List.of(firstEvaluation, secondEvaluation));
        when(geminiAnalysisClient.analyze(anyString())).thenReturn(geminiResponse());

        // 재분석 요청 실행
        AnalysisReResponse response = analysisService.reanalyze(
                new AnalysisReRequest(
                        1L,
                        "## 수정된 이력서\n- Java Spring Boot 프로젝트 경험\n- GitHub Actions 배포 경험",
                        "## 원본 공고\n- Java/Spring 필수\n- CI/CD 우대"
                )
        );

        // analysis_result 요약값이 갱신됐는지 확인
        assertThat(response.analysisResultId()).isEqualTo(1L);
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.greenCount()).isEqualTo(1);
        assertThat(response.yellowCount()).isEqualTo(1);
        assertThat(response.redCount()).isZero();
        assertThat(response.companyName()).isEqualTo("ResuFit");
        assertThat(response.positionTitle()).isEqualTo("백엔드 개발자");

        // 첫 번째 요건/평가가 확인됨 상태로 갱신됐는지 확인
        assertThat(firstRequirement.getCategory()).isEqualTo("REQUIRED");
        assertThat(firstRequirement.getTitle()).isEqualTo("Java/Spring 경험");
        assertThat(firstEvaluation.getMatchStatus()).isEqualTo("CONFIRMED");
        assertThat(firstEvaluation.getFeedback()).isNull();

        // 두 번째 요건/평가가 보강 필요 상태로 갱신됐는지 확인
        assertThat(secondRequirement.getCategory()).isEqualTo("PREFERRED");
        assertThat(secondRequirement.getTitle()).isEqualTo("CI/CD 파이프라인 경험");
        assertThat(secondEvaluation.getMatchStatus()).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(secondEvaluation.getResumeEvidence()).contains("GitHub Actions");
        assertThat(secondEvaluation.getRevisionSuggestion()).contains("자동 배포");
    }

    @Test
    void reanalyze_doesNotCallGeminiWhenRetryCountIsFiveOrMore() {
        // 재분석 횟수가 이미 5회인 분석 결과 준비
        AnalysisResult result = analysisResult();
        ReflectionTestUtils.setField(result, "retryCount", 5);

        when(analysisResultRepository.findById(1L)).thenReturn(Optional.of(result));

        // 재분석 제한에 걸리면 예외가 발생
        assertThatThrownBy(() -> analysisService.reanalyze(
                new AnalysisReRequest(1L, "## 수정된 이력서", "## 원본 공고")
        )).isInstanceOf(CustomException.class);

        // 제한 초과 시 Gemini API는 호출하지 않음
        verify(geminiAnalysisClient, never()).analyze(anyString());
    }

    private AnalysisResult analysisResult() {
        // 재분석 대상 analysis_result 테스트 객체 생성
        AnalysisResult result = AnalysisResult.builder()
                .userId(100L)
                .resumeNum(200L)
                .jdNum(300L)
                .companyName("기존 회사")
                .positionTitle("기존 포지션")
                .overallLevel("LOW")
                .redCount(2)
                .yellowCount(0)
                .greenCount(0)
                .build();
        ReflectionTestUtils.setField(result, "id", 1L);
        return result;
    }

    private JobRequirement jobRequirement(Long id, String category, String title) {
        // 재분석 대상 job_requirement 테스트 객체 생성
        JobRequirement requirement = JobRequirement.builder()
                .analysisResultId(1L)
                .category(category)
                .title(title)
                .description(title)
                .sourceText(title)
                .build();
        ReflectionTestUtils.setField(requirement, "id", id);
        return requirement;
    }

    private RequirementEvaluation requirementEvaluation(Long id, Long requirementId, String matchStatus) {
        // 재분석 대상 requirement_evaluation 테스트 객체 생성
        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .requirementId(requirementId)
                .analysisResultId(1L)
                .matchStatus(matchStatus)
                .resumeEvidence(null)
                .feedback(null)
                .revisionSuggestion(null)
                .build();
        ReflectionTestUtils.setField(evaluation, "id", id);
        return evaluation;
    }

    private GeminiAnalysisResponse geminiResponse() {
        // Gemini 재분석 응답 테스트 객체 생성
        return new GeminiAnalysisResponse(
                "ResuFit",
                "백엔드 개발자",
                new GeminiAnalysisSummary(
                        1,
                        1,
                        0,
                        0,
                        1,
                        "CI/CD 경험을 배포 자동화 성과 중심으로 보강하세요"
                ),
                List.of(
                        new GeminiRequirementResult(
                                "req_1",
                                "Java/Spring 경험",
                                "required",
                                "met",
                                null,
                                null,
                                null,
                                null
                        ),
                        new GeminiRequirementResult(
                                "req_2",
                                "CI/CD 파이프라인 경험",
                                "preferred",
                                "partial",
                                "yellow",
                                "이력서 근거: GitHub Actions 언급 확인",
                                "CI/CD 도구는 확인되지만 배포 자동화 범위와 성과가 부족합니다.",
                                "GitHub Actions로 자동 배포 파이프라인을 구성한 경험이 있다면 배포 시간 단축 등 성과를 함께 작성하세요."
                        )
                )
        );
    }
}
