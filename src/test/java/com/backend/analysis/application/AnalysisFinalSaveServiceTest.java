package com.backend.analysis.application;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.response.AnalysisFinalSaveResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisFinalSaveServiceTest {

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
    @DisplayName("분석 결과 최종 저장 시 이력서 편집본과 최종 저장 시각을 저장한다")
    void finalSaveAnalysisUpdatesResumeContentAndFinalSavedAt() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        AnalysisFinalSaveResponse response = analysisService.finalSaveAnalysis(
                1L,
                1L,
                " 최종 저장할 이력서 텍스트입니다. "
        );

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getSaved()).isTrue();
        assertThat(response.getResumeCurrentText()).isEqualTo("최종 저장할 이력서 텍스트입니다.");
        assertThat(response.getResumeLastSavedAt()).isNotNull();
        assertThat(response.getFinalSavedAt()).isNotNull();
        assertThat(response.getResumeLastSavedAt()).isEqualTo(response.getFinalSavedAt());
        verify(analysisResultRepository).flush();
    }

    @Test
    @DisplayName("최종 저장할 이력서 텍스트가 공백이면 거부한다")
    void finalSaveAnalysisRejectsBlankResumeContent() {
        assertThatThrownBy(() -> analysisService.finalSaveAnalysis(1L, 1L, "   "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_RESUME_CONTENT);
    }

    private AnalysisResult createAnalysisResult(Long userId) {
        User user = User.createSocialUser(
                null,
                Provider.KAKAO,
                "kakao-provider-id",
                "카카오사용자"
        );
        ReflectionTestUtils.setField(user, "id", userId);

        UserResume userResume = UserResume.builder()
                .user(user)
                .resumeContent("기존 이력서 텍스트")
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

        return analysisResult;
    }
}
