package com.backend.analysis.application;

import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.Satisfaction;
import com.backend.analysis.dto.response.AnalysisDeleteResponse;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
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

    @Transactional
    public AnalysisSatisfactionResponse updateSatisfaction(Long userId, Long analysisResultId, String satisfactionValue) {
        AnalysisResult analysisResult = analysisResultRepository.findByIdAndDeletedAtIsNull(analysisResultId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

        if (!analysisResult.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN);
        }

        analysisResult.updateSatisfaction(parseSatisfaction(satisfactionValue));
        analysisResultRepository.flush();

        return AnalysisSatisfactionResponse.from(analysisResult);
    }

    @Transactional
    public AnalysisDeleteResponse deleteAnalysisResult(Long userId, Long analysisResultId) {
        AnalysisResult analysisResult = analysisResultRepository.findByIdAndDeletedAtIsNull(analysisResultId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

        if (!analysisResult.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN);
        }

        analysisResult.delete(LocalDateTime.now());
        analysisResultRepository.flush();

        return AnalysisDeleteResponse.from(analysisResult);
    }

    private Satisfaction parseSatisfaction(String satisfactionValue) {
        return switch (satisfactionValue.trim()) {
            case "LIKE" -> Satisfaction.LIKE;
            case "DISLIKE" -> Satisfaction.DISLIKE;
            case "NULL" -> null;
            default -> throw new CustomException(ErrorCode.INVALID_ANALYSIS_SATISFACTION);
        };
    }
}
