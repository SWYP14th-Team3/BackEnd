package com.backend.analysis.application;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.UserResume;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisManualLlmTest {

    private static final String JOB_URL = "https://www.jobkorea.co.kr/Recruit/GI_Read/49582513?Oem_Code=C1&sc=54&gno=49582513";
    private static final Path RESUME_PATH = Path.of("/Users/banjaehyeon/Desktop/채용공고/반재현_이력서_260628.pdf");

    private static final String JOB_POSTING_TEXT = """
            정보보안 담당

            문피아는 네이버웹툰(Team Webtoon)의 일원으로서
            그룹 공통 보안 표준을 내재화하며 One Team 거버넌스 체계를 완성해가고 있습니다.

            본 포지션은 자산·계정·정책 전반의 보안 통제를 운영하고,
            보안 솔루션 등 기술적 보호대책 이행을 함께 담당합니다.

            M IT GRC 조직은 문피아의 IT 거버넌스, 정보보안, 개인정보보호 전반을 책임지는 조직입니다.

            📝 업무내용

            · 정보보호 정책·지침 수립 및 운영
            · 정보자산·계정·접근권한 통제 체계 운영 및 정기 점검
            · 신규 솔루션·SaaS 도입 및 인프라 변경에 대한 보안성 검토
            · 보안 솔루션(WAF·EDR·DLP 등) 운영 및 보안 이벤트 대응
            · 취약점 진단 결과 조치 관리 및 개발팀 보안 협업
            · 연간 사업계획에 따른 IT·보안 예산 편성 및 집행 관리
            · 임직원 보안 교육 및 피싱 모의훈련 기획·운영
            · 그룹(네이버웹툰) 공통 보안 체계 협업 및 내재화

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

            [문피아 M IT GRC 조직의 AI 활용 환경]
            · 활용 도구: Claude / Claude Code, Gemini, Cursor 등
            · 개발 경험이 없어도 AI 도구를 통해 자신의 업무 생산성을 높이는 문화를 지향합니다

            📌진형 절차 및 안내 사항

            - 전형 절차: 서류 전형 ▶ 기업문화적합도 검사 ▶ 1차 인터뷰 ▶ 2차 인터뷰
            - 고용 형태: 정규직
            - 근무 장소: 서울 강남구 강남대로 308, 랜드마크타워 12층
            - 전형 절차는 일정 및 상황에 따라 변경될 수 있습니다.
            - 지원서 내용 중 허위사실이 있는 경우에는 합격이 취소될 수 있습니다.
            - 국가유공자 및 장애인 등 취업보호대상자는 관계법령에 따라 우대합니다.
            - 문의사항은 "채용 홈페이지 > 1:1 문의"로 접수해주시기 바랍니다.
            """;

    @Test
    @DisplayName("실제 LLM 분석 요청에서 크롤링 실패 URL의 입력 text를 채용공고 원본 데이터로 반환받는다")
    @EnabledIfEnvironmentVariable(named = "RUN_LLM_ANALYSIS_TEST", matches = "true")
    void createAnalysisWithRealLlmReturnsOriginalJobPostingTextWhenUrlCrawlFails() throws IOException {
        assumeTrue(System.getenv("GEMINI_API_KEY") != null && !System.getenv("GEMINI_API_KEY").isBlank());
        assumeTrue(Files.exists(RESUME_PATH), "이력서 PDF 파일이 존재해야 합니다: " + RESUME_PATH);

        AnalysisService service = analysisServiceWithRealGemini();
        MockMultipartFile resumeFile = new MockMultipartFile(
                "resumeFile",
                RESUME_PATH.getFileName().toString(),
                "application/pdf",
                Files.readAllBytes(RESUME_PATH)
        );

        AnalysisDetailResponse response = service.createAnalysis(
                1L,
                JobInputType.URL,
                JOB_URL,
                JOB_POSTING_TEXT,
                resumeFile,
                List.of()
        );

        assertThat(response.getJobInputType()).isEqualTo(JobInputType.URL);
        assertThat(response.getJobUrl()).isEqualTo(JOB_URL);
        assertThat(response.getJobOriginalText()).contains("정보보안 담당");
        assertThat(response.getJobOriginalText()).contains("문피아는 네이버웹툰");
        assertThat(response.getJobOriginalText()).contains("IT 운영·정보보안·IT 거버넌스 분야 실무 경험 3년 이상");
        assertThat(response.getJobOriginalText()).contains("보안 솔루션(WAF, IPS, EDR, SIEM 등) 운영 또는 정책 관리 경험");
        assertThat(response.getRequirements()).isNotEmpty();

        printManualAnalysisResult(response);
    }

    private void printManualAnalysisResult(AnalysisDetailResponse response) {
        System.out.println("\n================ LLM 분석 수동 확인 결과 ================");
        System.out.println("companyName       = " + response.getCompanyName());
        System.out.println("positionTitle     = " + response.getPositionTitle());
        System.out.println("jobInputType      = " + response.getJobInputType());
        System.out.println("jobUrl            = " + response.getJobUrl());
        System.out.println("jobPlatform       = " + response.getJobPlatform());
        System.out.println("overallLevel      = " + response.getOverallLevel());
        System.out.println("red/yellow/green  = "
                + response.getRedCount() + "/"
                + response.getYellowCount() + "/"
                + response.getGreenCount());

        System.out.println("\n---------------- 채용공고 원본 데이터(jobOriginalText) ----------------");
        System.out.println(response.getJobOriginalText());

        System.out.println("\n---------------- 채용공고 요약 데이터(jobSummaryText) ----------------");
        System.out.println(response.getJobSummaryText());

        System.out.println("\n---------------- 요건 분석 결과 ----------------");
        response.getRequirements().forEach(requirement -> {
            System.out.println("- [" + requirement.getRequirementType() + "] " + requirement.getTitle());
            System.out.println("  status         = " + requirement.getEvaluation().getMatchStatus());
            System.out.println("  displayTitle   = " + requirement.getEvaluation().getDisplayTitle());
            System.out.println("  judgeReason    = " + requirement.getEvaluation().getJudgeReason());
            System.out.println("  feedback       = " + requirement.getEvaluation().getFeedback());
            System.out.println("  priorityScore  = " + requirement.getEvaluation().getPriorityScore());
        });
        System.out.println("==========================================================\n");
    }

    private AnalysisService analysisServiceWithRealGemini() {
        JobPostingCrawler jobPostingCrawler = mock(JobPostingCrawler.class);
        GeminiAnalysisClient geminiAnalysisClient = new GeminiAnalysisClient(
                new ObjectMapper(),
                System.getenv("GEMINI_API_KEY"),
                System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.1-flash-lite")
        );
        UserRepository userRepository = mock(UserRepository.class);
        UserResumeRepository userResumeRepository = mock(UserResumeRepository.class);
        JobDescriptionRepository jobDescriptionRepository = mock(JobDescriptionRepository.class);
        JobPostingImageRepository jobPostingImageRepository = mock(JobPostingImageRepository.class);
        AnalysisResultRepository analysisResultRepository = mock(AnalysisResultRepository.class);
        JobRequirementRepository jobRequirementRepository = mock(JobRequirementRepository.class);
        RequirementEvaluationRepository requirementEvaluationRepository = mock(RequirementEvaluationRepository.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(User.createSocialUser(
                "manual-test@example.com",
                Provider.GOOGLE,
                "manual-test-user",
                "수동 테스트 유저"
        )));
        when(jobPostingCrawler.extractText(JOB_URL))
                .thenThrow(new CustomException(ErrorCode.JOB_POSTING_CRAWL_ERROR));
        when(jobPostingCrawler.extractPlatform(JOB_URL)).thenReturn("JOBKOREA");
        when(userResumeRepository.save(any(UserResume.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobDescriptionRepository.save(any(JobDescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobPostingImageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisResultRepository.save(any(AnalysisResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRequirementRepository.save(any(JobRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requirementEvaluationRepository.save(any(RequirementEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return new AnalysisService(
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
    }
}
