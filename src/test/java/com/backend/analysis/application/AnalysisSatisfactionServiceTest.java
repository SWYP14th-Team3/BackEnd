package com.backend.analysis.application;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.Satisfaction;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisSatisfactionServiceTest {

    private final AnalysisResultRepository analysisResultRepository = mock(AnalysisResultRepository.class);
    private final AnalysisService analysisService = new AnalysisService(
            null,
            null,
            null,
            null,
            null,
            null,
            analysisResultRepository,
            null,
            null
    );

    @Test
    @DisplayName("분석 만족도는 LIKE로 저장할 수 있다")
    void updateSatisfactionLikesAnalysisResult() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 6, 22, 15);
        ReflectionTestUtils.setField(analysisResult, "updatedAt", updatedAt);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        AnalysisSatisfactionResponse response = analysisService.updateSatisfaction(1L, 1L, "LIKE");

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getSatisfaction()).isEqualTo(Satisfaction.LIKE);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(analysisResult.getSatisfaction()).isEqualTo(Satisfaction.LIKE);
        verify(analysisResultRepository).flush();
    }

    @Test
    @DisplayName("분석 만족도는 DISLIKE로 저장할 수 있다")
    void updateSatisfactionDislikesAnalysisResult() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        AnalysisSatisfactionResponse response = analysisService.updateSatisfaction(1L, 1L, "DISLIKE");

        assertThat(response.getSatisfaction()).isEqualTo(Satisfaction.DISLIKE);
        assertThat(analysisResult.getSatisfaction()).isEqualTo(Satisfaction.DISLIKE);
        verify(analysisResultRepository).flush();
    }

    @Test
    @DisplayName("분석 만족도 선택 취소는 null로 저장한다")
    void updateSatisfactionClearsAnalysisResultSatisfaction() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        analysisResult.updateSatisfaction(Satisfaction.LIKE);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        AnalysisSatisfactionResponse response = analysisService.updateSatisfaction(1L, 1L, null);

        assertThat(response.getSatisfaction()).isNull();
        assertThat(analysisResult.getSatisfaction()).isNull();
        verify(analysisResultRepository).flush();
    }

    @Test
    @DisplayName("허용되지 않는 만족도 값은 거부한다")
    void updateSatisfactionRejectsInvalidValue() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        assertThatThrownBy(() -> analysisService.updateSatisfaction(1L, 1L, "GOOD"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ANALYSIS_SATISFACTION);
    }

    @Test
    @DisplayName("다른 사용자의 분석 만족도 저장은 접근 권한 오류로 거부한다")
    void updateSatisfactionRejectsOtherUserAnalysisResult() {
        AnalysisResult analysisResult = createAnalysisResult(2L);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        assertThatThrownBy(() -> analysisService.updateSatisfaction(1L, 1L, "LIKE"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ANALYSIS_RESULT_FORBIDDEN);
    }

    private AnalysisResult createAnalysisResult(Long userId) {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id-" + userId,
                "카카오사용자"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("이력서 텍스트")
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
}
