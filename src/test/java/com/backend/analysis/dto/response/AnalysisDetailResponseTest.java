package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
import com.backend.analysis.domain.Satisfaction;
import com.backend.analysis.domain.UserResume;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisDetailResponseTest {

    @Test
    @DisplayName("분석 상세 응답은 명세 필드를 분석 결과, 이력서, 공고, 요건 평가에서 조합한다")
    void responseContainsAnalysisDetailContractFields() {
        User user = User.createSocialUser(
                "user@gmail.com",
                Provider.GOOGLE,
                "google-provider-id",
                "강인성"
        );

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("현재 이력서 텍스트")
                .resumeFileName("resume.pdf")
                .resumeFileSize(480029L)
                .build();

        JobDescription jobDescription = JobDescription.builder()
                .user(user)
                .jobInputType(JobInputType.URL)
                .jobUrl("https://company.com/jobs/123")
                .companyName("카카오")
                .positionTitle("백엔드 개발자")
                .jobPlatform("company")
                .jdOriginalText("공고 원문 텍스트")
                .jdSummaryText("공고 요약 텍스트")
                .build();

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(user)
                .userResume(userResume)
                .jobDescription(jobDescription)
                .overallLevel(OverallLevel.LOW)
                .redCount(3)
                .yellowCount(2)
                .greenCount(1)
                .build();

        LocalDateTime resumeLastSavedAt = LocalDateTime.of(2026, 7, 23, 16, 20);
        LocalDateTime lastReanalyzedAt = LocalDateTime.of(2026, 7, 23, 16, 30);
        LocalDateTime finalSavedAt = LocalDateTime.of(2026, 7, 23, 16, 25);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 6, 21, 30);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 23, 16, 30);

        ReflectionTestUtils.setField(analysisResult, "id", 1L);
        ReflectionTestUtils.setField(analysisResult, "createdAt", createdAt);
        ReflectionTestUtils.setField(analysisResult, "updatedAt", updatedAt);

        userResume.updateResumeContent("현재 이력서 텍스트", resumeLastSavedAt);
        analysisResult.applyReanalysis(OverallLevel.MEDIUM, 1, 2, 3, lastReanalyzedAt);
        analysisResult.markSaved(finalSavedAt);
        analysisResult.updateSatisfaction(Satisfaction.LIKE);

        JobRequirement requirement = JobRequirement.builder()
                .analysisResult(analysisResult)
                .requirementType(RequirementType.REQUIRED)
                .category(RequirementCategory.QUALIFICATION)
                .title("Spring Boot 개발 경험")
                .description("Spring Boot 기반 백엔드 개발 경험이 필요합니다.")
                .jdEvidence("Spring Boot 기반 백엔드 개발 경험 보유자")
                .inputOrder(1)
                .build();
        ReflectionTestUtils.setField(requirement, "id", 10L);

        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.yellow)
                .displayTitle("Spring Boot 경험을 더 구체화하세요")
                .resumeEvidence("Spring Boot 프로젝트 경험은 있으나 구체적인 역할 설명이 부족함")
                .judgeReason("Spring Boot 경험은 확인되지만 공고가 요구하는 실무 적용 근거가 부족합니다.")
                .feedback("관련 경험은 확인되지만 어떤 기능을 구현했는지 명확하지 않습니다.")
                .revisionSuggestion("Spring Boot로 JWT 인증 API를 구현한 경험을 구체적으로 작성해보세요.")
                .effectScore(4)
                .effortScore(2)
                .priorityScore(BigDecimal.valueOf(8.0))
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(evaluation, "id", 100L);

        AnalysisDetailResponse response = AnalysisDetailResponse.from(
                analysisResult,
                List.of(JobRequirementResponse.from(requirement, evaluation))
        );

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getCompanyName()).isEqualTo("카카오");
        assertThat(response.getPositionTitle()).isEqualTo("백엔드 개발자");
        assertThat(response.getOverallLevel()).isEqualTo(OverallLevel.MEDIUM);
        assertThat(response.getRedCount()).isEqualTo(1);
        assertThat(response.getYellowCount()).isEqualTo(2);
        assertThat(response.getGreenCount()).isEqualTo(3);
        assertThat(response.getPreviousOverallLevel()).isEqualTo(OverallLevel.LOW);
        assertThat(response.getPreviousRedCount()).isEqualTo(3);
        assertThat(response.getPreviousYellowCount()).isEqualTo(2);
        assertThat(response.getPreviousGreenCount()).isEqualTo(1);
        assertThat(response.getLastReanalyzedAt()).isEqualTo(lastReanalyzedAt);
        assertThat(response.getRetryCount()).isEqualTo(1);
        assertThat(response.getRemainingRetryCount()).isEqualTo(4);
        assertThat(response.getSatisfaction()).isEqualTo(Satisfaction.LIKE);
        assertThat(response.getJobInputType()).isEqualTo(JobInputType.URL);
        assertThat(response.getJobUrl()).isEqualTo("https://company.com/jobs/123");
        assertThat(response.getJobPlatform()).isEqualTo("company");
        assertThat(response.getJobOriginalText()).isEqualTo("공고 원문 텍스트");
        assertThat(response.getJobSummaryText()).isEqualTo("공고 요약 텍스트");
        assertThat(response.getResumeCurrentText()).isEqualTo("현재 이력서 텍스트");
        assertThat(response.getResumeFileName()).isEqualTo("resume.pdf");
        assertThat(response.getResumeFileSize()).isEqualTo(480029L);
        assertThat(response.getResumeLastSavedAt()).isEqualTo(resumeLastSavedAt);
        assertThat(response.getFinalSavedAt()).isEqualTo(finalSavedAt);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);

        JobRequirementResponse requirementResponse = response.getRequirements().get(0);
        assertThat(requirementResponse.getRequirementId()).isEqualTo(10L);
        assertThat(requirementResponse.getRequirementType()).isEqualTo("REQUIRED");
        assertThat(requirementResponse.getCategory()).isEqualTo("자격요건");
        assertThat(requirementResponse.getTitle()).isEqualTo("Spring Boot 개발 경험");
        assertThat(requirementResponse.getDescription()).isEqualTo("Spring Boot 기반 백엔드 개발 경험이 필요합니다.");
        assertThat(requirementResponse.getJdEvidence()).isEqualTo("Spring Boot 기반 백엔드 개발 경험 보유자");
        assertThat(requirementResponse.getInputOrder()).isEqualTo(1);
        assertThat(requirementResponse.getEvaluation().getEvaluationId()).isEqualTo(100L);
        assertThat(requirementResponse.getEvaluation().getMatchStatus()).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(requirementResponse.getEvaluation().getDisplayTitle()).isEqualTo("Spring Boot 경험을 더 구체화하세요");
        assertThat(requirementResponse.getEvaluation().getResumeEvidence())
                .isEqualTo("Spring Boot 프로젝트 경험은 있으나 구체적인 역할 설명이 부족함");
        assertThat(requirementResponse.getEvaluation().getJudgeReason())
                .isEqualTo("Spring Boot 경험은 확인되지만 공고가 요구하는 실무 적용 근거가 부족합니다.");
        assertThat(requirementResponse.getEvaluation().getFeedback())
                .isEqualTo("관련 경험은 확인되지만 어떤 기능을 구현했는지 명확하지 않습니다.");
        assertThat(requirementResponse.getEvaluation().getRevisionSuggestion())
                .isEqualTo("Spring Boot로 JWT 인증 API를 구현한 경험을 구체적으로 작성해보세요.");
        assertThat(requirementResponse.getEvaluation().getEffectScore()).isEqualTo(4);
        assertThat(requirementResponse.getEvaluation().getEffortScore()).isEqualTo(2);
        assertThat(requirementResponse.getEvaluation().getPriorityScore()).isEqualByComparingTo(BigDecimal.valueOf(8.0));
        assertThat(requirementResponse.getEvaluation().getSortOrder()).isEqualTo(1);
    }
}
