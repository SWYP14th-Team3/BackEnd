package com.backend.analysis.dto.response;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class JobRequirementResponseTest {

    @Test
    @DisplayName("요건 카테고리는 프론트 명세의 한글 라벨로 응답한다")
    void categoryUsesKoreanLabel() {
        JobRequirement requirement = JobRequirement.builder()
                .category(RequirementCategory.WORK_COMPETENCY)
                .title("Spring Boot 개발 경험")
                .description("Spring Boot 기반 백엔드 개발 경험이 필요합니다.")
                .sourceText("Spring Boot 기반 백엔드 개발 경험 보유자")
                .requirementType(RequirementType.REQUIRED)
                .inputOrder(1)
                .build();

        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.yellow)
                .displayTitle("Spring Boot 경험을 더 구체화하세요")
                .resumeEvidence("Spring Boot 프로젝트 경험")
                .judgeReason("Spring Boot 경험은 확인되지만 실무 근거가 부족합니다.")
                .feedback("구체적인 역할 설명이 부족합니다.")
                .revisionSuggestion("JWT 인증 API 구현 경험을 구체적으로 작성하세요.")
                .effectScore(4)
                .effortScore(2)
                .priorityScore(BigDecimal.valueOf(8.0))
                .sortOrder(1)
                .build();

        JobRequirementResponse response = JobRequirementResponse.from(requirement, evaluation);

        assertThat(response.getRequirementType()).isEqualTo("REQUIRED");
        assertThat(response.getCategory()).isEqualTo("업무역량");
        assertThat(response.getJdEvidence()).isEqualTo("Spring Boot 기반 백엔드 개발 경험 보유자");
        assertThat(response.getInputOrder()).isEqualTo(1);
        assertThat(response.getEvaluation().getMatchStatus()).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(response.getEvaluation().getDisplayTitle()).isEqualTo("Spring Boot 경험을 더 구체화하세요");
        assertThat(response.getEvaluation().getJudgeReason()).isEqualTo("Spring Boot 경험은 확인되지만 실무 근거가 부족합니다.");
        assertThat(response.getEvaluation().getEffectScore()).isEqualTo(4);
        assertThat(response.getEvaluation().getEffortScore()).isEqualTo(2);
        assertThat(response.getEvaluation().getPriorityScore()).isEqualByComparingTo(BigDecimal.valueOf(8.0));
        assertThat(response.getEvaluation().getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("충족된 요건은 CONFIRMED로 응답하고 우선순위 점수는 null로 유지한다")
    void confirmedRequirementHasNoPriorityScores() {
        JobRequirement requirement = JobRequirement.builder()
                .category(RequirementCategory.PREFERENCE)
                .title("AWS 배포 경험")
                .description("AWS 환경에서 서비스를 배포한 경험을 우대합니다.")
                .sourceText("AWS, Docker 기반 배포 경험 우대")
                .requirementType(RequirementType.PREFERRED)
                .inputOrder(2)
                .build();

        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.green)
                .displayTitle("AWS 배포 경험이 확인됐어요")
                .resumeEvidence("AWS EC2와 Docker를 이용한 Spring Boot 서버 배포 경험 확인")
                .judgeReason("공고의 우대사항과 관련된 AWS 배포 경험이 이력서에서 확인됩니다.")
                .feedback("공고의 우대사항과 관련된 경험이 명확히 확인됩니다.")
                .revisionSuggestion(null)
                .effectScore(null)
                .effortScore(null)
                .priorityScore(null)
                .sortOrder(2)
                .build();

        JobRequirementResponse response = JobRequirementResponse.from(requirement, evaluation);

        assertThat(response.getRequirementType()).isEqualTo("PREFERRED");
        assertThat(response.getCategory()).isEqualTo("우대사항");
        assertThat(response.getEvaluation().getMatchStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getEvaluation().getEffectScore()).isNull();
        assertThat(response.getEvaluation().getEffortScore()).isNull();
        assertThat(response.getEvaluation().getPriorityScore()).isNull();
    }
}
