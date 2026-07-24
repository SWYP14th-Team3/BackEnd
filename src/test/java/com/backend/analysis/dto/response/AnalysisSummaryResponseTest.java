package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.UserResume;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisSummaryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("분석 목록 항목은 finalSavedAt 키로 최종 저장 시각을 응답한다")
    void summaryResponseUsesFinalSavedAtField() throws Exception {
        AnalysisResult analysisResult = createAnalysisResult();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 6, 21, 30);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 6, 21, 45);
        LocalDateTime finalSavedAt = LocalDateTime.of(2026, 7, 6, 21, 40);

        ReflectionTestUtils.setField(analysisResult, "id", 1L);
        ReflectionTestUtils.setField(analysisResult, "createdAt", createdAt);
        ReflectionTestUtils.setField(analysisResult, "updatedAt", updatedAt);
        analysisResult.markSaved(finalSavedAt);

        AnalysisSummaryResponse response = AnalysisSummaryResponse.from(analysisResult);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getCompanyName()).isEqualTo("카카오");
        assertThat(response.getPositionTitle()).isEqualTo("백엔드 개발자");
        assertThat(response.getOverallLevel()).isEqualTo(OverallLevel.MEDIUM);
        assertThat(response.getRedCount()).isEqualTo(2);
        assertThat(response.getYellowCount()).isEqualTo(3);
        assertThat(response.getGreenCount()).isEqualTo(5);
        assertThat(response.getRetryCount()).isZero();
        assertThat(response.getRemainingRetryCount()).isEqualTo(5);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.getFinalSavedAt()).isEqualTo(finalSavedAt);
        assertThat(json).contains("\"finalSavedAt\"");
        assertThat(json).doesNotContain("\"lastSavedAt\"");
    }

    @Test
    @DisplayName("분석 목록 페이지 응답은 빈 목록도 200 응답 데이터 형태로 표현한다")
    void emptyPageResponseKeepsPageMetadata() {
        AnalysisPageResponse<AnalysisSummaryResponse> response = AnalysisPageResponse.from(
                new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 10),
                        0
                )
        );

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getLast()).isTrue();
    }

    private AnalysisResult createAnalysisResult() {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("이력서")
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

        return AnalysisResult.builder()
                .user(user)
                .userResume(userResume)
                .jobDescription(jobDescription)
                .overallLevel(OverallLevel.MEDIUM)
                .redCount(2)
                .yellowCount(3)
                .greenCount(5)
                .build();
    }
}
