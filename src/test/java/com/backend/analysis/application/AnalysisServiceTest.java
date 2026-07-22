package com.backend.analysis.application;

import com.backend.analysis.domain.JobInputType;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AnalysisServiceTest {

    private final AnalysisService analysisService = new AnalysisService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    @DisplayName("PDF 헤더가 있으면 MIME 타입이 달라도 통과한다")
    void validatePdfAcceptsPdfHeaderWithDifferentContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/octet-stream",
                "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII)
        );

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(analysisService, "validatePdf", file));
    }

    @Test
    @DisplayName("PDF 헤더 앞에 BOM이나 공백이 있어도 통과한다")
    void validatePdfAcceptsHeaderNearBeginning() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "\uFEFF \n%PDF-1.7\ncontent".getBytes(StandardCharsets.UTF_8)
        );

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(analysisService, "validatePdf", file));
    }

    @Test
    @DisplayName("PDF 헤더가 없으면 MIME 타입이 PDF여도 거부한다")
    void validatePdfRejectsFileWithoutPdfHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "not a pdf".getBytes()
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(analysisService, "validatePdf", file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PDF_FILE);
    }

    @Test
    @DisplayName("URL 입력 방식은 jobUrl만 허용한다")
    void validateJobPostingInputAcceptsOnlyJobUrlForUrlType() {
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                null
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                "직접 입력 공고 텍스트"
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("TEXT 입력 방식은 jobText만 허용하고 길이 조건을 검증한다")
    void validateJobPostingInputAcceptsOnlyJobTextForTextType() {
        String validJobText = "a".repeat(100);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                null,
                validJobText
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                "https://company.com/jobs/123",
                validJobText
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                null,
                "a".repeat(99)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
