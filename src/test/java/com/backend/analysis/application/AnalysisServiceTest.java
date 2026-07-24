package com.backend.analysis.application;

import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.dto.GeminiPriorityScoreResult;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("10MB를 초과한 PDF는 거부한다")
    void validatePdfRejectsPdfOver10Mb() {
        byte[] bytes = new byte[(10 * 1024 * 1024) + 1];
        byte[] header = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                bytes
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(analysisService, "validatePdf", file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PDF_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("텍스트 기반 PDF에서 이력서 텍스트를 추출한다")
    void extractResumeTextFromTextBasedPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createTextPdf("Spring Boot backend developer")
        );

        String text = ReflectionTestUtils.invokeMethod(analysisService, "extractResumeText", file);

        assertThat(text).contains("Spring Boot backend developer");
    }

    @Test
    @DisplayName("텍스트를 읽을 수 없는 PDF는 거부한다")
    void extractResumeTextRejectsPdfWithoutText() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createBlankPdf()
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(analysisService, "extractResumeText", file))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNREADABLE_PDF_TEXT);
    }

    @Test
    @DisplayName("URL 입력 방식은 jobUrl만 허용한다")
    void validateJobPostingInputAcceptsOnlyJobUrlForUrlType() {
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                null,
                null
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                "직접 입력 공고 텍스트",
                null
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
                validJobText,
                null
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                "https://company.com/jobs/123",
                validJobText,
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                null,
                "a".repeat(99),
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("우선순위 점수는 effect 제곱을 effort로 나누고 소수점 둘째 자리로 반올림한다")
    void calculatePriorityScoreSquaresEffectAndDividesByEffort() {
        GeminiPriorityScoreResult priorityScore = new GeminiPriorityScoreResult(
                "r1",
                5,
                3,
                "테스트"
        );

        BigDecimal result = ReflectionTestUtils.invokeMethod(
                analysisService,
                "calculatePriorityScore",
                priorityScore
        );

        assertThat(result).isEqualByComparingTo("8.33");
    }

    @Test
    @DisplayName("필수 red가 2개 이상이면 적합도 점수와 관계없이 하로 계산한다")
    void calculateOverallLevelForTwoRequiredRedsReturnsLow() {
        OverallLevel result = ReflectionTestUtils.invokeMethod(
                analysisService,
                "calculateOverallLevel",
                List.of(
                        requirement("r1", "필수", "red"),
                        requirement("r2", "필수", "red"),
                        requirement("r3", "우대", "green")
                )
        );

        assertThat(result).isEqualTo(OverallLevel.LOW);
    }

    @Test
    @DisplayName("필수 red가 1개면 적합도 점수가 높아도 최대 중으로 계산한다")
    void calculateOverallLevelForOneRequiredRedCapsAtMedium() {
        OverallLevel result = ReflectionTestUtils.invokeMethod(
                analysisService,
                "calculateOverallLevel",
                List.of(
                        requirement("r1", "필수", "red"),
                        requirement("r2", "필수", "green"),
                        requirement("r3", "필수", "green"),
                        requirement("r4", "필수", "green"),
                        requirement("r5", "우대", "green")
                )
        );

        assertThat(result).isEqualTo(OverallLevel.MEDIUM);
    }

    private byte[] createTextPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createBlankPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private GeminiRequirementResult requirement(String reqId, String importance, String status) {
        return new GeminiRequirementResult(
                reqId,
                reqId + " 요건",
                importance,
                reqId + " 공고 근거",
                reqId + " 이력서 근거",
                reqId + " 판단 이유",
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                null
        );
    }
}
