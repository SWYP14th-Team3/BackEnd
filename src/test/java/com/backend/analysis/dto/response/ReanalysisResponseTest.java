package com.backend.analysis.dto.response;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.UserResume;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReanalysisResponseTest {

    @Test
    @DisplayName("재분석 응답은 직전 결과와 재분석 후 결과를 함께 담는다")
    void responseContainsPreviousAndCurrentAnalysisResult() {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("기존 이력서")
                .resumeFileName("resume.pdf")
                .resumeFileSize(100L)
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
                .yellowCount(1)
                .greenCount(0)
                .build();
        ReflectionTestUtils.setField(analysisResult, "id", 1L);

        LocalDateTime finalSavedAt = LocalDateTime.of(2026, 7, 23, 17, 20);
        LocalDateTime reanalyzedAt = LocalDateTime.of(2026, 7, 23, 17, 30);
        analysisResult.markSaved(finalSavedAt);
        userResume.updateResumeContent("수정된 이력서", reanalyzedAt);
        analysisResult.applyReanalysis(OverallLevel.HIGH, 0, 1, 1, reanalyzedAt);

        ReanalysisResponse response = ReanalysisResponse.from(analysisResult, List.of());

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getPreviousOverallLevel()).isEqualTo(OverallLevel.MEDIUM);
        assertThat(response.getPreviousRedCount()).isEqualTo(1);
        assertThat(response.getPreviousYellowCount()).isEqualTo(1);
        assertThat(response.getPreviousGreenCount()).isEqualTo(0);
        assertThat(response.getOverallLevel()).isEqualTo(OverallLevel.HIGH);
        assertThat(response.getRedCount()).isZero();
        assertThat(response.getYellowCount()).isEqualTo(1);
        assertThat(response.getGreenCount()).isEqualTo(1);
        assertThat(response.getLastReanalyzedAt()).isEqualTo(reanalyzedAt);
        assertThat(response.getRetryCount()).isEqualTo(1);
        assertThat(response.getRemainingRetryCount()).isEqualTo(4);
        assertThat(response.getResumeCurrentText()).isEqualTo("수정된 이력서");
        assertThat(response.getResumeLastSavedAt()).isEqualTo(reanalyzedAt);
        assertThat(response.getFinalSavedAt()).isNull();
    }

    @Test
    @DisplayName("변경 없는 재분석 응답은 직전 결과와 현재 결과를 동일하게 담는다")
    void unchangedResponseContainsSamePreviousAndCurrentAnalysisResult() {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("기존 이력서")
                .resumeFileName("resume.pdf")
                .resumeFileSize(100L)
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

        ReanalysisResponse response = ReanalysisResponse.unchanged(analysisResult, List.of());

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
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
    }
}
