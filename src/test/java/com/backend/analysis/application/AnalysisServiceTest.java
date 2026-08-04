package com.backend.analysis.application;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiCardContentResult;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
import com.backend.analysis.dto.GeminiPriorityScoreResult;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.dto.GeminiResumeResponse;
import com.backend.analysis.dto.response.AnalysisDetailResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.analysis.infrastructure.JobDescriptionRepository;
import com.backend.analysis.infrastructure.JobPostingImageRepository;
import com.backend.analysis.infrastructure.JobRequirementRepository;
import com.backend.analysis.infrastructure.RequirementEvaluationRepository;
import com.backend.analysis.infrastructure.UserResumeRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisServiceTest {

    private final AnalysisService analysisService = analysisService(null, null);

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
    @DisplayName("이력서 정리는 PDFBox로 추출한 텍스트를 Gemini 프롬프트에 전달한다")
    void summarizeResumeTextUsesExtractedPdfTextAsPromptSource() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createTextPdf("Spring Boot backend developer")
        );
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        AnalysisService service = analysisService(null, geminiAnalysisClient);
        when(geminiAnalysisClient.summarizeResume(anyString()))
                .thenReturn(new GeminiResumeResponse("정규화된 이력서", "resume_정리본.md"));

        String resumeText = ReflectionTestUtils.invokeMethod(service, "summarizeResumeText", file);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeResume(promptCaptor.capture());
        assertThat(resumeText).isEqualTo("정규화된 이력서");
        assertThat(promptCaptor.getValue()).contains("이력서 추출 텍스트");
        assertThat(promptCaptor.getValue()).contains("Spring Boot backend developer");
    }

    @Test
    @DisplayName("이력서 PDF와 채용공고 URL, 채용공고 text를 입력받아 분석 결과를 반환한다")
    void createAnalysisReturnsAnalysisResultFromResumePdfJobUrlAndJobText() throws IOException {
        Long userId = 1L;
        String jobUrl = "https://www.jobkorea.co.kr/Recruit/GI_Read/123";
        String jobText = "직접 입력 공고 텍스트입니다. Java Spring Boot 기반 REST API 개발 경험과 MySQL 운영 경험을 요구합니다.".repeat(2);
        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "resume.pdf",
                "application/pdf",
                createTextPdf("Spring Boot backend developer resume")
        );
        User user = User.createSocialUser(
                "test@example.com",
                Provider.GOOGLE,
                "google-1",
                "테스트 유저"
        );

        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserResumeRepository userResumeRepository = mock(UserResumeRepository.class);
        JobDescriptionRepository jobDescriptionRepository = mock(JobDescriptionRepository.class);
        JobPostingImageRepository jobPostingImageRepository = mock(JobPostingImageRepository.class);
        AnalysisResultRepository analysisResultRepository = mock(AnalysisResultRepository.class);
        JobRequirementRepository jobRequirementRepository = mock(JobRequirementRepository.class);
        RequirementEvaluationRepository requirementEvaluationRepository = mock(RequirementEvaluationRepository.class);
        AnalysisService service = new AnalysisService(
                jobPostingCrawler,
                geminiAnalysisClient,
                userRepository,
                userResumeRepository,
                jobDescriptionRepository,
                jobPostingImageRepository,
                analysisResultRepository,
                jobRequirementRepository,
                requirementEvaluationRepository
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userResumeRepository.save(any(UserResume.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobDescriptionRepository.save(any(JobDescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisResultRepository.save(any(AnalysisResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRequirementRepository.save(any(JobRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requirementEvaluationRepository.save(any(RequirementEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobPostingCrawler.extractPlatform(jobUrl)).thenReturn("JOBKOREA");
        when(geminiAnalysisClient.summarizeResume(anyString()))
                .thenReturn(new GeminiResumeResponse("Spring Boot 기반 REST API 구현 경험", "resume_정리본.md"));
        when(geminiAnalysisClient.summarizeJobDescription(anyString(), anyList()))
                .thenReturn(new GeminiJobDescriptionResponse(
                        true,
                        "테스트회사",
                        "백엔드 개발자",
                        "LLM이 정리한 공고 원문",
                        "공고 요약: 백엔드 개발자"
                ));
        when(geminiAnalysisClient.analyze(anyString()))
                .thenReturn(new GeminiAnalysisResponse(
                        true,
                        null,
                        "테스트회사",
                        "백엔드 개발자",
                        null,
                        List.of(
                                requirement("r1", "필수", "green"),
                                requirement("r2", "필수", "yellow")
                        )
                ));
        when(geminiAnalysisClient.scorePriorities(anyString()))
                .thenReturn(List.of(new GeminiPriorityScoreResult("r2", 4, 2, "필수 REST API 경험 보강 필요")));
        when(geminiAnalysisClient.createCardContents(anyString()))
                .thenReturn(List.of(
                        new GeminiCardContentResult("r1", "green", "r1 요건", "이미 잘 드러난 경험입니다.", null),
                        new GeminiCardContentResult("r2", "yellow", "REST API 경험 표현 보강", "역할과 성과를 더 구체화하면 좋아요.", "REST API 구현 성과를 수치로 추가하세요.")
                ));

        AnalysisDetailResponse response = service.createAnalysis(
                userId,
                JobInputType.URL,
                jobUrl,
                jobText,
                resumeFile,
                List.of()
        );

        assertThat(response.getJobInputType()).isEqualTo(JobInputType.URL);
        assertThat(response.getJobUrl()).isEqualTo(jobUrl);
        assertThat(response.getJobPlatform()).isEqualTo("JOBKOREA");
        assertThat(response.getJobOriginalText()).isEqualTo(jobText.trim());
        assertThat(response.getJobOriginalText()).contains("직접 입력 공고 텍스트");
        assertThat(response.getJobOriginalText()).doesNotContain("LLM이 정리한 공고 원문");
        assertThat(response.getJobSummaryText()).contains("공고 요약");
        assertThat(response.getResumeCurrentText()).contains("Spring Boot 기반 REST API 구현 경험");
        assertThat(response.getCompanyName()).isEqualTo("테스트회사");
        assertThat(response.getPositionTitle()).isEqualTo("백엔드 개발자");
        assertThat(response.getGreenCount()).isEqualTo(1);
        assertThat(response.getYellowCount()).isEqualTo(1);
        assertThat(response.getRedCount()).isZero();
        assertThat(response.getRequirements()).hasSize(2);
        assertThat(response.getRequirements().getFirst().getEvaluation().getMatchStatus()).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(response.getRequirements().getFirst().getEvaluation().getPriorityScore()).isEqualByComparingTo("8.00");
        assertThat(response.getRequirements().getFirst().getEvaluation().getDisplayTitle()).isEqualTo("REST API 경험 표현 보강");

        ArgumentCaptor<String> jobPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeJobDescription(jobPromptCaptor.capture(), anyList());
        assertThat(jobPromptCaptor.getValue()).contains("직접 입력 공고 텍스트");
        assertThat(jobPromptCaptor.getValue()).doesNotContain("URL 크롤링 공고 텍스트");
        verify(jobPostingCrawler, never()).extractText(jobUrl);

        ArgumentCaptor<String> analysisPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).analyze(analysisPromptCaptor.capture());
        assertThat(analysisPromptCaptor.getValue()).contains("Spring Boot 기반 REST API 구현 경험");
        assertThat(analysisPromptCaptor.getValue()).contains("공고 요약: 백엔드 개발자");
    }

    @Test
    @DisplayName("채용공고 URL 크롤링이 실패하면 입력 text를 원본 공고 데이터로 분석 결과에 반환한다")
    void createAnalysisReturnsOriginalJobTextWhenJobUrlCrawlFails() throws IOException {
        Long userId = 1L;
        String jobUrl = "https://recruit.munpiacorp.com/rcrt/view.do?annoId=30005150&sw=&subJobCdArr=&sysCompanyCdArr=&empTypeCdArr=&entTypeCdArr=&workAreaCdArr=";
        String jobPostingText = """
                정보보안 담당

                문피아는 네이버웹툰(Team Webtoon)의 일원으로서
                그룹 공통 보안 표준을 내재화하며 One Team 거버넌스 체계를 완성해가고 있습니다.

                📝 업무내용
                · 정보보호 정책·지침 수립 및 운영
                · 정보자산·계정·접근권한 통제 체계 운영 및 정기 점검
                · 신규 솔루션·SaaS 도입 및 인프라 변경에 대한 보안성 검토
                · 보안 솔루션(WAF·EDR·DLP 등) 운영 및 보안 이벤트 대응

                🎯자격요건
                · IT 운영·정보보안·IT 거버넌스 분야 실무 경험 3년 이상
                · 자산·계정·접근통제 등 관리적 보안 통제 수행 경험
                · 담당 업무를 독립적으로 수행하고 산출물을 스스로 완성해 본 경험
                · 다양한 이해관계자와 원활히 협업할 수 있는 커뮤니케이션 역량
                · 생성형 AI 도구를 업무에 실제 활용한 경험

                💪우대사항
                · 보안 솔루션(WAF, IPS, EDR, SIEM 등) 운영 또는 정책 관리 경험
                · 취약점 진단 또는 보안성 검토 수행 경험
                · 클라우드 환경(KT Cloud, NCP, AWS 등) 운영 또는 사용 경험
                · 정보보호 인증(ISMS-P, ISO 27001 등) 증적 대응 경험
                · 개인정보보호법 등 관련 법령에 대한 실무적 이해
                · AI 코딩 도구를 활용한 업무 자동화·스크립팅 경험
                """;
        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                "banjaehyeon_resume.pdf",
                "application/pdf",
                createTextPdf("Information security and IT operation resume")
        );
        User user = User.createSocialUser(
                "test@example.com",
                Provider.GOOGLE,
                "google-1",
                "테스트 유저"
        );

        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserResumeRepository userResumeRepository = mock(UserResumeRepository.class);
        JobDescriptionRepository jobDescriptionRepository = mock(JobDescriptionRepository.class);
        JobPostingImageRepository jobPostingImageRepository = mock(JobPostingImageRepository.class);
        AnalysisResultRepository analysisResultRepository = mock(AnalysisResultRepository.class);
        JobRequirementRepository jobRequirementRepository = mock(JobRequirementRepository.class);
        RequirementEvaluationRepository requirementEvaluationRepository = mock(RequirementEvaluationRepository.class);
        AnalysisService service = new AnalysisService(
                jobPostingCrawler,
                geminiAnalysisClient,
                userRepository,
                userResumeRepository,
                jobDescriptionRepository,
                jobPostingImageRepository,
                analysisResultRepository,
                jobRequirementRepository,
                requirementEvaluationRepository
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userResumeRepository.save(any(UserResume.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobDescriptionRepository.save(any(JobDescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisResultRepository.save(any(AnalysisResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRequirementRepository.save(any(JobRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requirementEvaluationRepository.save(any(RequirementEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobPostingCrawler.extractText(jobUrl))
                .thenThrow(new CustomException(ErrorCode.JOB_POSTING_CRAWL_ERROR));
        when(jobPostingCrawler.extractPlatform(jobUrl)).thenReturn("MUNPIA");
        when(geminiAnalysisClient.summarizeResume(anyString()))
                .thenReturn(new GeminiResumeResponse("정보보안 정책 운영 및 보안 솔루션 대응 경험", "resume_정리본.md"));
        when(geminiAnalysisClient.summarizeJobDescription(anyString(), anyList()))
                .thenReturn(new GeminiJobDescriptionResponse(
                        true,
                        "문피아",
                        "정보보안 담당",
                        jobPostingText,
                        "정보보안 담당 공고 요약"
                ));
        when(geminiAnalysisClient.analyze(anyString()))
                .thenReturn(new GeminiAnalysisResponse(
                        true,
                        null,
                        "문피아",
                        "정보보안 담당",
                        null,
                        List.of(requirement("r1", "필수", "yellow"))
                ));
        when(geminiAnalysisClient.scorePriorities(anyString()))
                .thenReturn(List.of(new GeminiPriorityScoreResult("r1", 4, 2, "정보보안 운영 경험 보강 필요")));
        when(geminiAnalysisClient.createCardContents(anyString()))
                .thenReturn(List.of(new GeminiCardContentResult(
                        "r1",
                        "yellow",
                        "정보보안 운영 경험 표현 보강",
                        "보안 정책 운영 경험을 더 구체적으로 적으면 좋아요.",
                        "WAF, EDR, DLP 운영 경험과 대응 성과를 추가하세요."
                )));

        AnalysisDetailResponse response = service.createAnalysis(
                userId,
                JobInputType.URL,
                jobUrl,
                jobPostingText,
                resumeFile,
                List.of()
        );

        assertThat(response.getCompanyName()).isEqualTo("문피아");
        assertThat(response.getPositionTitle()).isEqualTo("정보보안 담당");
        assertThat(response.getJobInputType()).isEqualTo(JobInputType.URL);
        assertThat(response.getJobUrl()).isEqualTo(jobUrl);
        assertThat(response.getJobPlatform()).isEqualTo("MUNPIA");
        assertThat(response.getJobOriginalText()).isEqualTo(jobPostingText.trim());
        assertThat(response.getJobOriginalText()).contains("정보보안 담당");
        assertThat(response.getJobOriginalText()).contains("IT 운영·정보보안·IT 거버넌스 분야 실무 경험 3년 이상");
        assertThat(response.getJobOriginalText()).contains("보안 솔루션(WAF, IPS, EDR, SIEM 등) 운영 또는 정책 관리 경험");
        assertThat(response.getJobSummaryText()).isEqualTo("정보보안 담당 공고 요약");
        assertThat(response.getRequirements()).hasSize(1);
        assertThat(response.getRequirements().getFirst().getEvaluation().getMatchStatus()).isEqualTo("NEEDS_IMPROVEMENT");

        ArgumentCaptor<String> jobPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeJobDescription(jobPromptCaptor.capture(), anyList());
        assertThat(jobPromptCaptor.getValue()).contains("문피아는 네이버웹툰");
        assertThat(jobPromptCaptor.getValue()).contains("정보보호 정책·지침 수립 및 운영");
        assertThat(jobPromptCaptor.getValue()).doesNotContain(jobUrl);
    }

    @Test
    @DisplayName("URL 입력 방식은 jobUrl과 보조 공고 텍스트를 함께 허용한다")
    void validateJobPostingInputAcceptsJobTextWithUrlType() {
        String validJobText = "a".repeat(100);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                null,
                null
        ));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                validJobText,
                null
        ));
    }

    @Test
    @DisplayName("URL 입력 방식의 보조 공고 텍스트는 길이 조건을 검증한다")
    void validateJobPostingInputValidatesFallbackJobTextWithUrlType() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                "a".repeat(99),
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_TEXT_TOO_SHORT);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.URL,
                "https://company.com/jobs/123",
                "a".repeat(6000),
                null
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.JOB_TEXT_TOO_LONG);
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
    @DisplayName("URL 입력에 보조 텍스트가 있으면 크롤링보다 보조 텍스트를 우선한다")
    void loadJobPostingDraftPrefersJobTextWhenUrlInputHasText() {
        String jobUrl = "https://company.com/jobs/123";
        String fallbackText = "백엔드 개발자 채용 공고입니다. Spring Boot 기반 API 개발과 MySQL 운영 경험을 요구합니다.".repeat(2);
        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        AnalysisService service = analysisService(jobPostingCrawler, geminiAnalysisClient);
        when(jobPostingCrawler.extractPlatform(jobUrl)).thenReturn("UNKNOWN");
        when(geminiAnalysisClient.summarizeJobDescription(anyString(), anyList()))
                .thenReturn(new GeminiJobDescriptionResponse(true, null, null, "raw text", "summary text"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service,
                "loadJobPostingDraft",
                JobInputType.URL,
                jobUrl,
                fallbackText,
                List.of()
        ));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeJobDescription(promptCaptor.capture(), anyList());
        assertThat(promptCaptor.getValue()).contains(fallbackText);
        assertThat(promptCaptor.getValue()).doesNotContain(jobUrl);
        verify(jobPostingCrawler, never()).extractText(jobUrl);
    }

    @Test
    @DisplayName("URL 크롤링이 실패해도 이미지가 있으면 OCR 결과로 채용공고 정리를 계속한다")
    void loadJobPostingDraftContinuesWithJobImageWhenUrlCrawlFails() {
        String jobUrl = "https://company.com/jobs/123";
        MockMultipartFile image = jobImage("job.png");
        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        AnalysisService service = analysisService(jobPostingCrawler, geminiAnalysisClient);
        when(jobPostingCrawler.extractText(jobUrl))
                .thenThrow(new CustomException(ErrorCode.JOB_POSTING_CRAWL_ERROR));
        when(jobPostingCrawler.extractPlatform(jobUrl)).thenReturn("UNKNOWN");
        when(geminiAnalysisClient.extractJobPostingImageText(anyList(), anyString()))
                .thenReturn("이미지 OCR로 읽은 자격요건: Java, Spring Boot 경험");
        when(geminiAnalysisClient.summarizeJobDescription(anyString(), anyList()))
                .thenReturn(new GeminiJobDescriptionResponse(true, null, null, "raw text", "summary text"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service,
                "loadJobPostingDraft",
                JobInputType.URL,
                jobUrl,
                null,
                List.of(image)
        ));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeJobDescription(promptCaptor.capture(), anyList());
        assertThat(promptCaptor.getValue()).contains("이미지 OCR로 읽은 자격요건");
        assertThat(promptCaptor.getValue()).doesNotContain(jobUrl);
        verify(geminiAnalysisClient).extractJobPostingImageText(anyList(), anyString());
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
    @DisplayName("IMAGE 입력 방식은 jobImages와 보조 URL을 허용하고 최대 10장까지 받는다")
    void validateJobPostingInputAcceptsJobImagesAndUrlForImageType() {
        List<MockMultipartFile> validImages = List.of(jobImage("job-1.png"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                null,
                validImages
        ));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                "https://company.com/jobs/123",
                null,
                validImages
        ));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                "ftp://company.com/jobs/123",
                null,
                validImages
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_JOB_URL);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                analysisService,
                "validateJobPostingInput",
                JobInputType.IMAGE,
                null,
                "직접 입력 공고 텍스트",
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
    @DisplayName("IMAGE 입력 방식에 URL이 있으면 크롤링 텍스트와 이미지 OCR 결과를 함께 정리한다")
    void loadJobPostingDraftCombinesCrawledTextAndImageTextForImageUrlInput() {
        String jobUrl = "https://company.com/jobs/123";
        MockMultipartFile image = jobImage("job.png");
        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = mock(GeminiAnalysisClient.class);
        AnalysisService service = analysisService(jobPostingCrawler, geminiAnalysisClient);
        when(jobPostingCrawler.extractText(jobUrl)).thenReturn("URL에서 읽은 자격요건: Spring Boot 경험");
        when(jobPostingCrawler.extractPlatform(jobUrl)).thenReturn("UNKNOWN");
        when(geminiAnalysisClient.extractJobPostingImageText(anyList(), anyString()))
                .thenReturn("이미지 OCR로 읽은 우대사항: AWS 운영 경험");
        when(geminiAnalysisClient.summarizeJobDescription(anyString(), anyList()))
                .thenReturn(new GeminiJobDescriptionResponse(true, null, null, "raw text", "summary text"));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                service,
                "loadJobPostingDraft",
                JobInputType.IMAGE,
                jobUrl,
                null,
                List.of(image)
        ));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiAnalysisClient).summarizeJobDescription(promptCaptor.capture(), anyList());
        assertThat(promptCaptor.getValue()).contains("URL에서 읽은 자격요건");
        assertThat(promptCaptor.getValue()).contains("이미지 OCR로 읽은 우대사항");
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
    @DisplayName("우대 요건이 red로 반환되면 yellow로 보정한다")
    void normalizePreferredMissingStatusToYellow() {
        List<GeminiRequirementResult> result = ReflectionTestUtils.invokeMethod(
                analysisService,
                "normalizePreferredMissingStatuses",
                List.of(requirement("r1", "우대", "red"))
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().matchStatus()).isEqualTo("yellow");
    }

    @Test
    @DisplayName("적합도는 Green 1.0, Yellow 0.5와 필수 0.7 우대 0.3 기준으로 계산한다")
    void calculateOverallLevelUsesSpecifiedWeightsAndYellowScore() {
        OverallLevel result = ReflectionTestUtils.invokeMethod(
                analysisService,
                "calculateOverallLevel",
                List.of(
                        requirement("r1", "필수", "green"),
                        requirement("r2", "필수", "yellow")
                )
        );

        assertThat(result).isEqualTo(OverallLevel.MEDIUM);
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
    @DisplayName("최초 분석 프롬프트는 우대 요건 red 금지 규칙을 포함한다")
    void buildAnalysisPromptContainsPreferredCannotBeRedRule() {
        String prompt = ReflectionTestUtils.invokeMethod(
                analysisService,
                "buildAnalysisPrompt",
                "Spring Boot 프로젝트 경험",
                "Spring Boot 경험 우대"
        );

        assertThat(prompt).contains("우대 요건은 red가 될 수 없다");
        assertThat(prompt).contains("우대의 바닥은 yellow다");
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
        assertThat(prompt).contains("URL 크롤링 텍스트가 비어 있어도 텍스트/OCR/첨부 이미지 중 하나에서 공고 본문을 읽을 수 있으면 성공으로 처리한다");
        assertThat(prompt).contains("URL 크롤링 텍스트, 입력 텍스트, 이미지 OCR 텍스트, 첨부 이미지 모두에서 채용공고 본문을 읽을 수 없음");
        assertThat(prompt).doesNotContain("URL 접근 불가 / 페이지 로드 실패");
        assertThat(prompt).contains("공고 본문을 있는 그대로 확보한다");
        assertThat(prompt).contains("이 단계에서는 요건을 추출·평가하지 마라");
        assertThat(prompt).contains("원문의 줄·항목 구조를 최대한 보존한다");
        assertThat(prompt).contains("raw_text를 한 문장 요약이나 콤마로 압축하지 마라");
        assertThat(prompt).contains("company_name");
        assertThat(prompt).contains("position_title");
        assertThat(prompt).contains("회사명과 포지션명은 summary_text에만 쓰지 말고");
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

    private AnalysisService analysisService(
            JobPostingCrawler jobPostingCrawler,
            GeminiAnalysisClient geminiAnalysisClient
    ) {
        return new AnalysisService(
                jobPostingCrawler,
                geminiAnalysisClient,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
