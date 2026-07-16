package com.backend.analysis.application;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;

    @Transactional
    public AnalysisSaveResponse saveResume(Long userId, Long analysisResultId, String resumeCurrentText) {
        AnalysisResult analysisResult = analysisResultRepository.findByIdAndDeletedAtIsNull(analysisResultId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

        if (!analysisResult.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN);
        }

        analysisResult.updateResumeCurrentText(resumeCurrentText, LocalDateTime.now());
        analysisResultRepository.flush();

        return AnalysisSaveResponse.from(analysisResult);
    }
}
