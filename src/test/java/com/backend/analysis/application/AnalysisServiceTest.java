package com.backend.analysis.application;

import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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
                "%PDF-1.7\ncontent".getBytes()
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
}
