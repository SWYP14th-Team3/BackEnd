package com.backend.analysis.application;

import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
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
import java.util.Map;
import java.util.stream.IntStream;

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
                .isEqualTo(ErrorCode.RESUME_LOAD_FAILED);
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
                .isEqualTo(ErrorCode.INVALID_JOB_URL);
    }

    @Test
    @DisplayName("URL 입력 방식은 채용공고 이미지를 함께 첨부할 수 있다")
    void validateJobPostingInputAllowsJobImagesWithUrlType() {
        MockMultipartFile image = new MockMultipartFile(
                "jobImages",
                "job.png",
                "image/png",
                "image".getBytes(StandardCharsets.UTF_8)
        );

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                null,
                List.of(image)
        ));
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
                "a".repeat(6000),
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_TEXT_TOO_LONG);

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
                .isEqualTo(ErrorCode.JOB_TEXT_TOO_SHORT);
    }

    @Test
    @DisplayName("IMAGE 입력 방식은 jobImages만 허용하고 최대 10장까지 받는다")
    void validateJobPostingInputAcceptsOnlyJobImagesForImageType() {
        List<MockMultipartFile> validImages = List.of(jobImage("job-1.png"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                validImages
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                "https://company.com/jobs/123",
                null,
                validImages
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        List<MockMultipartFile> invalidImages = List.of(new MockMultipartFile(
                "jobImages",
                "job-1.gif",
                "image/gif",
                "image".getBytes(StandardCharsets.UTF_8)
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                invalidImages
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_JOB_IMAGE_FORMAT);

        List<MockMultipartFile> tooManyImages = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> jobImage("job-" + index + ".png"))
                .toList();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                tooManyImages
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_JOB_IMAGES);
    }

    @Test
    @DisplayName("회사명 검색어가 공백이면 거부한다")
    void getAnalysesRejectsBlankCompanyNameSearchKeyword() {
        assertThatThrownBy(() -> analysisService.getAnalyses(1L, 0, 10, "   "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMPANY_NAME_REQUIRED);
    }

    @Test
    @DisplayName("TEXT 입력 방식은 채용공고 이미지를 함께 첨부할 수 있다")
    void validateJobPostingInputAllowsJobImagesWithTextType() {
        MockMultipartFile image = new MockMultipartFile(
                "jobImages",
                "job.png",
                "image/png",
                "image".getBytes(StandardCharsets.UTF_8)
        );

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.TEXT,
                null,
                "a".repeat(100),
                List.of(image)
        ));
    }

    @Test
    @DisplayName("채용공고 이미지는 최대 10장까지 허용한다")
    void validateJobPostingInputAllowsUpToTenJobImages() {
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                createJobImages(10)
        ));
    }

    @Test
    @DisplayName("채용공고 이미지가 10장을 초과하면 거부한다")
    void validateJobPostingInputRejectsMoreThanTenJobImages() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                createJobImages(11)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOO_MANY_JOB_IMAGES);
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

    @Test
    @DisplayName("재분석 프롬프트는 확정 요건을 참고만 하고 판정 필드만 출력하도록 요청한다")
    void buildReanalysisPromptRequestsEvaluationOnlyOutput() {
        JobRequirement requirement = JobRequirement.builder()
                .requirementType(RequirementType.REQUIRED)
                .category(RequirementCategory.QUALIFICATION)
                .title("React 기반 개발 경험")
                .jdEvidence("자격요건: React 기반 개발 경험")
                .inputOrder(0)
                .build();
        RequirementEvaluation previousEvaluation = RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.CONFIRMED)
                .displayTitle("React 기반 개발 경험")
                .resumeEvidence("React 기반 대시보드 설계 및 구현")
                .judgeReason("React 프로젝트 경험이 확인됩니다.")
                .feedback("React 경험이 명확합니다.")
                .sortOrder(1)
                .build();

        String prompt = ReflectionTestUtils.invokeMethod(
                analysisService,
                "buildReanalysisPrompt",
                List.of(requirement),
                Map.of("r1", previousEvaluation),
                "React 기반 대시보드 렌더링 30% 개선"
        );

        assertThat(prompt).contains("content / importance / jd_evidence는 참고만 하고 출력하지 마라");
        assertThat(prompt).contains("previous_status: green");
        assertThat(prompt).contains("previous_resume_evidence: React 기반 대시보드 설계 및 구현");
        assertThat(prompt).contains("previous_judge_reason: React 프로젝트 경험이 확인됩니다.");
        assertThat(prompt).contains("previous_resume_evidence가 수정된 이력서에도 그대로 존재하면 status를 낮추지 마라");
        assertThat(prompt).contains("status, resume_evidence, judge_reason");
        assertThat(prompt).contains("\"req_id\": \"r1\"");
        assertThat(prompt).contains("\"status\": \"green\"");
        assertThat(prompt).contains("\"resume_evidence\"");
        assertThat(prompt).contains("\"judge_reason\"");
        assertThat(prompt).doesNotContain("\"content\": \"React 기반 개발 경험\"");
        assertThat(prompt).doesNotContain("\"importance\": \"필수\"");
        assertThat(prompt).doesNotContain("\"jd_evidence\": \"자격요건: React 기반 개발 경험\"");
        assertThat(prompt).contains("렌더링 30% 개선");
        assertThat(prompt).contains("React 기반 대시보드 렌더링 30% 개선");
    }

    @Test
    @DisplayName("카드 문구 프롬프트는 상세 피드백과 한끗 피드백을 분리해 생성하도록 요청한다")
    void buildCardContentPromptRequestsRevisionSuggestion() {
        String prompt = ReflectionTestUtils.invokeMethod(
                analysisService,
                "buildCardContentPrompt",
                List.of(requirement("r1", "필수", "yellow")),
                Map.of("r1", new GeminiPriorityScoreResult("r1", 4, 2, "테스트")),
                "Spring Boot 개발 경험 보유자",
                "Spring Boot 프로젝트 경험"
        );

        assertThat(prompt).contains("상세 피드백(feedback)");
        assertThat(prompt).contains("한끗 피드백(revision_suggestion)");
        assertThat(prompt).contains("\"revision_suggestion\"");
        assertThat(prompt).contains("yellow/red는 반드시 작성한다");
        assertThat(prompt).contains("green은 수정 제안이 필수는 아니므로 null로 둔다");
    }

    @Test
    @DisplayName("채용공고 프롬프트는 입력값을 유지하면서 원문 텍스트와 요약을 요구한다")
    void buildJobDescriptionPromptRequestsRawTextAndSummaryWithoutBreakingInputs() {
        String prompt = ReflectionTestUtils.invokeMethod(
                analysisService,
                "buildJobDescriptionPrompt",
                "https://example.com/job",
                "모집분야: 정보보안 담당",
                "업무내용: 정보보호 정책 수립 및 운영",
                "잡코리아",
                List.of(jobImage("posting-1.png"))
        );

        assertThat(prompt).contains("공고의 원문 텍스트를 확보한다");
        assertThat(prompt).contains("입력 URL에서 이미 크롤링된 텍스트를 기준으로 공고 본문을 확인한다");
        assertThat(prompt).contains("첨부 이미지와 이미지 OCR 텍스트를 함께 참고해 공고 내용을 읽는다");
        assertThat(prompt).contains("공고 본문을 있는 그대로 확보한다");
        assertThat(prompt).contains("이 단계에서는 요건을 추출·평가하지 마라");
        assertThat(prompt).contains("원문의 줄·항목 구조를 최대한 보존한다");
        assertThat(prompt).contains("raw_text를 한 문장 요약이나 콤마로 압축하지 마라");
        assertThat(prompt).contains("summary_text");
        assertThat(prompt).contains("공고 요약 마크다운");
        assertThat(prompt).contains("inline_data로 함께 첨부");
        assertThat(prompt).contains("posting-1.png");
        assertThat(prompt).contains("입력 URL");
        assertThat(prompt).contains("https://example.com/job");
        assertThat(prompt).contains("입력 텍스트");
        assertThat(prompt).contains("모집분야: 정보보안 담당");
        assertThat(prompt).contains("이미지 OCR 텍스트");
        assertThat(prompt).contains("업무내용: 정보보호 정책 수립 및 운영");
    }

    @Test
    @DisplayName("채용공고 이미지 OCR 프롬프트는 직무 상세를 빠짐없이 추출하도록 요구한다")
    void buildJobPostingImageOcrPromptRequestsJobDetailExtraction() {
        String prompt = ReflectionTestUtils.invokeMethod(
                analysisService,
                "buildJobPostingImageOcrPrompt",
                List.of(jobImage("posting-1.png"))
        );

        assertThat(prompt).contains("첨부된 채용공고 이미지들을 OCR로 읽어 텍스트로 옮겨라");
        assertThat(prompt).contains("업무내용, 자격요건, 우대사항, 포지션 소개, 조직 소개는 반드시 포함한다");
        assertThat(prompt).contains("posting-1.png");
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

    private MockMultipartFile jobImage(String fileName) {
        return new MockMultipartFile(
                "jobImages",
                fileName,
                "image/png",
                "image".getBytes(StandardCharsets.UTF_8)
        );
    }

    private List<MockMultipartFile> createJobImages(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new MockMultipartFile(
                        "jobImages",
                        "job-" + index + ".png",
                        "image/png",
                        ("image-" + index).getBytes(StandardCharsets.UTF_8)
                ))
                .toList();
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
