package com.backend.analysis.application;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
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

class AnalysisResumeSaveServiceTest {

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
    @DisplayName("이력서 자동저장은 편집본 저장 시각을 갱신하고 최종 저장 시각을 null로 변경한다")
    void saveResumeUpdatesDraftAndClearsFinalSavedAt() {
        AnalysisResult analysisResult = createAnalysisResult(1L);
        analysisResult.markSaved(LocalDateTime.of(2026, 7, 6, 21, 40));
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        AnalysisSaveResponse response = analysisService.saveResume(
                1L,
                1L,
                "수정된 이력서 텍스트입니다."
        );

        assertThat(response.getAnalysisResultId()).isEqualTo(1L);
        assertThat(response.getResumeCurrentText()).isEqualTo("수정된 이력서 텍스트입니다.");
        assertThat(response.getResumeLastSavedAt()).isNotNull();
        assertThat(response.getFinalSavedAt()).isNull();
        assertThat(analysisResult.getFinalSavedAt()).isNull();
        verify(analysisResultRepository).flush();
    }

    @Test
    @DisplayName("다른 사용자의 분석 결과 자동저장은 수정 권한 오류로 거부한다")
    void saveResumeRejectsOtherUserAnalysisResult() {
        AnalysisResult analysisResult = createAnalysisResult(2L);
        when(analysisResultRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(analysisResult));

        assertThatThrownBy(() -> analysisService.saveResume(1L, 1L, "수정된 이력서 텍스트입니다."))
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
                .resumeContent("기존 이력서 텍스트")
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
