package com.backend.analysis.dto.response;

import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
                .build();

        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.NEEDS_IMPROVEMENT)
                .resumeEvidence("Spring Boot 프로젝트 경험")
                .feedback("구체적인 역할 설명이 부족합니다.")
                .revisionSuggestion("JWT 인증 API 구현 경험을 구체적으로 작성하세요.")
                .build();

        JobRequirementResponse response = JobRequirementResponse.from(requirement, evaluation);

        assertThat(response.getCategory()).isEqualTo("업무역량");
        assertThat(response.getEvaluation().getMatchStatus()).isEqualTo(MatchStatus.NEEDS_IMPROVEMENT);
    }
}
