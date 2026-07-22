package com.backend.analysis.service;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.User;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.AnalysisResponse;
import com.backend.analysis.dto.AnalysisReRequest;
import com.backend.analysis.dto.AnalysisReResponse;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.dto.GeminiResumeResponse;
import com.backend.analysis.dto.RequirementResponse;
import com.backend.analysis.repository.AnalysisResultRepository;
import com.backend.analysis.repository.JobDescriptionRepository;
import com.backend.analysis.repository.JobRequirementRepository;
import com.backend.analysis.repository.RequirementEvaluationRepository;
import com.backend.analysis.repository.UserRepository;
import com.backend.analysis.repository.UserResumeRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisService {

    // 분석/재분석 프롬프트 참고자료 JSON 파일 경로
    private static final Path ANALYSIS_PROMPT_REFERENCES_PATH = Path.of(
            "src/main/java/com/backend/global/common/prompt/References_01.json"
    );

    // 채용공고 URL 텍스트 추출 담당
    private final JobPostingCrawler jobPostingCrawler;
    // Gemini API 호출 담당
    private final GeminiAnalysisClient geminiAnalysisClient;
    // 사용자 테이블 접근
    private final UserRepository userRepository;
    // 이력서 테이블 접근
    private final UserResumeRepository userResumeRepository;
    // 채용공고 테이블 접근
    private final JobDescriptionRepository jobDescriptionRepository;
    // 분석 결과 테이블 접근
    private final AnalysisResultRepository analysisResultRepository;
    // 채용공고 요건 테이블 접근
    private final JobRequirementRepository jobRequirementRepository;
    // 요건 평가 테이블 접근
    private final RequirementEvaluationRepository requirementEvaluationRepository;
    // 프롬프트 참고자료 JSON 파싱
    private final ObjectMapper objectMapper;

    public AnalysisService(
            JobPostingCrawler jobPostingCrawler,
            GeminiAnalysisClient geminiAnalysisClient,
            UserRepository userRepository,
            UserResumeRepository userResumeRepository,
            JobDescriptionRepository jobDescriptionRepository,
            AnalysisResultRepository analysisResultRepository,
            JobRequirementRepository jobRequirementRepository,
            RequirementEvaluationRepository requirementEvaluationRepository,
            ObjectMapper objectMapper
    ) {
        this.jobPostingCrawler = jobPostingCrawler;
        this.geminiAnalysisClient = geminiAnalysisClient;
        this.userRepository = userRepository;
        this.userResumeRepository = userResumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.requirementEvaluationRepository = requirementEvaluationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisResponse analyze(
            Long userId,
            String jobPostingUrl,
            String jobPostingText,
            MultipartFile jobPostingImage,
            MultipartFile resumePdf
    ) {
        validatePdf(resumePdf); // pdf검증
        validateJobPostingInput(jobPostingUrl, jobPostingText, jobPostingImage); // 채용공고 검증

        User user = userRepository.findById(userId) // 유저 정보 불러오기
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 이력서 PDF >> 마크다운으로 변경
        GeminiResumeResponse resumeResponse = geminiAnalysisClient.summarizeResume(
                resumePdf,
                buildResumePrompt(resumePdf.getOriginalFilename())
        );
        // 이력서 정보 저장
        UserResume resume = userResumeRepository.save(
                UserResume.builder()
                        .userId(user.getId())
                        .resumeContent(resumeResponse.resumeContent())
                        .resumeFileName(defaultIfBlank(
                                resumeResponse.resumeFileName(),
                                buildResumeFileName(resumePdf.getOriginalFilename())
                        ))
                        .build()
        );

        String crawledText = crawlJobPostingText(jobPostingUrl, jobPostingText); // 채용공고 가져오기
        String platform = jobPostingCrawler.extractPlatform(jobPostingUrl);
        GeminiJobDescriptionResponse jobDescriptionResponse = geminiAnalysisClient.summarizeJobDescription(
                buildJobDescriptionPrompt(jobPostingUrl, crawledText, platform),
                jobPostingImage
        );
        JobDescription jobDescription = jobDescriptionRepository.save(
                JobDescription.builder()
                        .userId(user.getId())
                        .companyName(jobDescriptionResponse.companyName())
                        .positionTitle(jobDescriptionResponse.positionTitle())
                        .jobPlatform(defaultIfBlank(jobDescriptionResponse.jobPlatform(), platform))
                        .jdContent(jobDescriptionResponse.jdContent())
                        .build()
        );

        GeminiAnalysisResponse analysisResponse = geminiAnalysisClient.analyze(
                buildAnalysisPrompt(resume.getResumeContent(), jobDescription.getJdContent())
        );
        validateAnalysisResponse(analysisResponse);

        CountResult countResult = countStatus(analysisResponse.requirements());
        AnalysisResult result = analysisResultRepository.save(
                AnalysisResult.builder()
                        .userId(user.getId())
                        .resumeNum(resume.getId())
                        .jdNum(jobDescription.getId())
                        .companyName(jobDescription.getCompanyName())
                        .positionTitle(jobDescription.getPositionTitle())
                        .overallLevel(normalizeOverallLevel(analysisResponse.overallLevel()))
                        .redCount(countResult.redCount())
                        .yellowCount(countResult.yellowCount())
                        .greenCount(countResult.greenCount())
                        .build()
        );

        List<RequirementResponse> requirementResponses = saveRequirementsAndEvaluations(
                result,
                analysisResponse.requirements()
        );

        return AnalysisResponse.of(result, resume, jobDescription, requirementResponses);
    }

    @Transactional
    public AnalysisReResponse reanalyze(AnalysisReRequest request) {
        // 재분석 요청값 검증
        validateReanalysisRequest(request);

        // 기존 분석 결과 조회
        AnalysisResult result = analysisResultRepository.findById(request.analysis_result_id())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 재분석은 최대 5회까지만 허용
        if (result.getRetryCount() >= 5) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 수정된 이력서와 원본 공고로 Gemini 재분석 요청
        GeminiAnalysisResponse analysisResponse = geminiAnalysisClient.analyze(
                buildReanalysisPrompt(
                        request.resume_content(),
                        request.jd_content()
                )
        );
        validateAnalysisResponse(analysisResponse);

        // 재분석 결과 요약 카운트 계산
        CountResult countResult = countStatus(analysisResponse.requirements());
        result.updateReanalysis(
                analysisResponse.company(),
                analysisResponse.position(),
                normalizeOverallLevel(analysisResponse.overallLevel()),
                countResult.redCount(),
                countResult.yellowCount(),
                countResult.greenCount()
        );

        // 기존 요건/평가 row를 같은 analysis_result_id 기준으로 갱신
        List<RequirementResponse> requirementResponses = updateRequirementsAndEvaluations(
                result.getId(),
                analysisResponse.requirements()
        );

        return AnalysisReResponse.of(result, requirementResponses);
    }

    private void validateAnalysisResponse(GeminiAnalysisResponse analysisResponse) {
        // Gemini 결과가 비어 있으면 DB 저장 전에 명확한 오류로 중단
        if (analysisResponse == null
                || analysisResponse.requirements() == null
                || analysisResponse.requirements().isEmpty()) {
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }
    }

    private void validateReanalysisRequest(AnalysisReRequest request) {
        // 재분석에 필요한 필수값이 비어 있으면 중단
        if (request == null
                || request.analysis_result_id() == null
                || request.resume_content() == null
                || request.resume_content().isBlank()
                || request.jd_content() == null
                || request.jd_content().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<RequirementResponse> saveRequirementsAndEvaluations(
            AnalysisResult result,
            List<GeminiRequirementResult> requirementResults
    ) {
        if (requirementResults == null) {
            return List.of();
        }

        List<RequirementResponse> responses = new ArrayList<>();

        for (GeminiRequirementResult item : requirementResults) {
            // Gemini 상태값을 DB 저장값으로 변환
            String matchStatus = normalizeMatchStatus(item.matchStatus());
            JobRequirement requirement = jobRequirementRepository.save(
                    JobRequirement.builder()
                            .analysisResultId(result.getId())
                            .category(normalizeCategory(item.category()))
                            .title(defaultIfBlank(item.title(), "요건"))
                            .description(item.description())
                            .sourceText(item.sourceText())
                            .build()
            );

            RequirementEvaluation evaluation = requirementEvaluationRepository.save(
                    RequirementEvaluation.builder()
                            .requirementId(requirement.getId())
                            .analysisResultId(result.getId())
                            .matchStatus(matchStatus)
                            .resumeEvidence(normalizeEvaluationText(matchStatus, item.resumeEvidence()))
                            .feedback(normalizeEvaluationText(matchStatus, item.feedback()))
                            .revisionSuggestion(normalizeEvaluationText(matchStatus, item.revisionSuggestion()))
                            .build()
            );

            responses.add(RequirementResponse.from(requirement, evaluation));
        }

        return responses;
    }

    private List<RequirementResponse> updateRequirementsAndEvaluations(
            Long analysisResultId,
            List<GeminiRequirementResult> requirementResults
    ) {
        if (requirementResults == null) {
            return List.of();
        }

        List<JobRequirement> requirements = jobRequirementRepository.findByAnalysisResultIdOrderByIdAsc(analysisResultId);
        List<RequirementEvaluation> evaluations =
                requirementEvaluationRepository.findByAnalysisResultIdOrderByRequirementIdAsc(analysisResultId);
        Map<Long, RequirementEvaluation> evaluationByRequirementId = new HashMap<>();

        // requirement_id 기준으로 기존 평가를 빠르게 찾기 위한 Map 구성
        for (RequirementEvaluation evaluation : evaluations) {
            evaluationByRequirementId.put(evaluation.getRequirementId(), evaluation);
        }

        List<RequirementResponse> responses = new ArrayList<>();
        // Gemini 응답과 기존 요건 중 공통으로 존재하는 개수만 갱신
        int updateSize = Math.min(requirements.size(), requirementResults.size());

        for (int i = 0; i < updateSize; i++) {
            GeminiRequirementResult item = requirementResults.get(i);
            JobRequirement requirement = requirements.get(i);
            String matchStatus = normalizeMatchStatus(item.matchStatus());

            requirement.updateReanalysis(
                    normalizeCategory(item.category()),
                    defaultIfBlank(item.title(), "요건"),
                    item.description(),
                    item.sourceText()
            );

            RequirementEvaluation evaluation = evaluationByRequirementId.get(requirement.getId());

            if (evaluation == null) {
                // 기존 평가 row가 없으면 새로 생성
                evaluation = requirementEvaluationRepository.save(
                        RequirementEvaluation.builder()
                                .requirementId(requirement.getId())
                                .analysisResultId(analysisResultId)
                                .matchStatus(matchStatus)
                                .resumeEvidence(normalizeEvaluationText(matchStatus, item.resumeEvidence()))
                                .feedback(normalizeEvaluationText(matchStatus, item.feedback()))
                                .revisionSuggestion(normalizeEvaluationText(matchStatus, item.revisionSuggestion()))
                        .build()
                );
            } else {
                // 기존 평가 row가 있으면 내용만 갱신
                evaluation.updateReanalysis(
                        matchStatus,
                        normalizeEvaluationText(matchStatus, item.resumeEvidence()),
                        normalizeEvaluationText(matchStatus, item.feedback()),
                        normalizeEvaluationText(matchStatus, item.revisionSuggestion())
                );
            }

            responses.add(RequirementResponse.from(requirement, evaluation));
        }

        return responses;
    }

    private void validatePdf(MultipartFile resumePdf) {
        if (resumePdf == null || resumePdf.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }

        if (!"application/pdf".equals(resumePdf.getContentType())) {
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }
    }

    private void validateJobPostingInput(
            String jobPostingUrl,
            String jobPostingText,
            MultipartFile jobPostingImage
    ) {
        boolean hasUrl = jobPostingUrl != null && !jobPostingUrl.isBlank();
        boolean hasText = jobPostingText != null && !jobPostingText.isBlank();
        boolean hasImage = jobPostingImage != null && !jobPostingImage.isEmpty();

        if (!hasUrl && !hasText && !hasImage) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String crawlJobPostingText(String jobPostingUrl, String jobPostingText) {
        // 직접 입력한 공고 텍스트가 있으면 URL 크롤링보다 우선 사용
        if (jobPostingText != null && !jobPostingText.isBlank()) {
            return jobPostingText.trim();
        }

        if (jobPostingUrl != null && !jobPostingUrl.isBlank()) {
            return jobPostingCrawler.extractText(jobPostingUrl.trim());
        }

        return "";
    }

    private CountResult countStatus(List<GeminiRequirementResult> requirements) {
        // 화면 색상 카운트로 사용할 red/yellow/green 개수 계산
        int red = 0;
        int yellow = 0;
        int green = 0;

        if (requirements == null) {
            return new CountResult(red, yellow, green);
        }

        for (GeminiRequirementResult requirement : requirements) {
            String status = normalizeMatchStatus(requirement.matchStatus());
            String flag = requirement.flag();

            if ("red".equals(flag)) {
                red++;
            } else if ("yellow".equals(flag)) {
                yellow++;
            } else if ("CONFIRMED".equals(status)) {
                green++;
            } else if ("MISSING".equals(status)) {
                red++;
            } else if ("NEEDS_IMPROVEMENT".equals(status)) {
                yellow++;
            }
        }

        return new CountResult(red, yellow, green);
    }

    private String normalizeCategory(String category) {
        // Gemini 응답의 type/category 값을 DB 저장값으로 변환
        if ("required".equals(category)) {
            return "REQUIRED";
        }
        if ("preferred".equals(category)) {
            return "PREFERRED";
        }
        if ("자격요건".equals(category)) {
            return "REQUIRED";
        }
        if ("업무역량".equals(category)) {
            return "WORK_SKILL";
        }
        if ("도메인".equals(category)) {
            return "DOMAIN";
        }
        if ("우대사항".equals(category)) {
            return "PREFERRED";
        }
        if ("REQUIRED".equals(category)
                || "WORK_SKILL".equals(category)
                || "DOMAIN".equals(category)
                || "PREFERRED".equals(category)) {
            return category;
        }

        return "REQUIRED";
    }

    private String normalizeMatchStatus(String status) {
        // Gemini 응답의 status 값을 DB 저장값으로 변환
        if ("met".equals(status)) {
            return "CONFIRMED";
        }
        if ("partial".equals(status)) {
            return "NEEDS_IMPROVEMENT";
        }
        if ("gap".equals(status)) {
            return "MISSING";
        }
        if ("확인됨".equals(status)) {
            return "CONFIRMED";
        }
        if ("보강 필요".equals(status)) {
            return "NEEDS_IMPROVEMENT";
        }
        if ("없음".equals(status)) {
            return "MISSING";
        }
        if ("CONFIRMED".equals(status)
                || "NEEDS_IMPROVEMENT".equals(status)
                || "MISSING".equals(status)) {
            return status;
        }

        return "NEEDS_IMPROVEMENT";
    }

    private String normalizeOverallLevel(String level) {
        // 알 수 없는 전체 레벨은 중간값으로 보정
        if ("HIGH".equals(level)
                || "MEDIUM".equals(level)
                || "LOW".equals(level)) {
            return level;
        }

        return "MEDIUM";
    }

    private String normalizeEvaluationText(String matchStatus, String value) {
        // 확인됨 항목은 화면 카드에 피드백 영역을 만들지 않도록 비워둠
        if ("CONFIRMED".equals(matchStatus)) {
            return null;
        }

        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }

        return value;
    }

    private String defaultIfBlank(String value, String fallback) {
        // 값이 없을 때 사용할 기본값 처리
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    private String buildResumeFileName(String originalFilename) {
        // 원본 PDF 파일명에서 마크다운 정리본 파일명 생성
        String source = defaultIfBlank(originalFilename, "resume.pdf");
        String normalized = source.replaceAll("(?i)\\.pdf$", "");
        return normalized + "_정리본.md";
    }

    private String nowText() {
        // 프롬프트에 넣을 현재 시각 생성
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).toString();
    }

    private String buildResumePrompt(String originalFilename) {
        // 이력서 PDF를 마크다운 JSON으로 정리하기 위한 프롬프트 생성
        return """
                주어진 [이력서 원본 데이터]를 아래의 [작성 규칙]과 [출력 포맷(JSON)]에 맞추어 완벽한 JSON 형식으로 변환해줘.

                ### [작성 규칙]
                1. 주제 분류: 마크다운 텍스트 생성 시 반드시 '기술스택', '프로젝트', '학력', '활동' 4가지 주요 섹션으로 구분하여 작성할 것.
                2. 텍스트 정제: 원본의 줄바꿈 오류나 깨진 문자를 자연스럽게 다듬고, 불필요한 미사여구는 배제하고 개발자 이력서에 걸맞은 간결하고 명확한 문체(개조식)를 사용할 것.
                3. 기술스택 구체화: 사용 언어, 프레임워크, 데이터베이스, 인프라/협업 도구 등을 카테고리별로 명확히 분류할 것.
                4. 프로젝트 상세화: 프로젝트는 [프로젝트명 / 기간 / 역할 / 기술 스택 / 주요 성과 및 구현 내용]이 한눈에 보이도록 구조화할 것.
                5. JSON 에스케이프 규칙: resumeContent 내부의 줄바꿈과 쌍따옴표는 JSON 문법에 맞게 처리할 것.

                ### [마크다운 템플릿]
                # [이름 입력] | [한 줄 소개 또는 희망 직무]

                ## 기술 스택 (Technical Skills)
                * **Languages:**
                * **Frameworks & Libraries:**
                * **Databases:**
                * **Infra / DevOps / Tools:**

                ## 프로젝트 (Projects)
                ### 1. [프로젝트명]
                * **진행 기간:** YYYY.MM ~ YYYY.MM
                * **담당 역할:**
                * **사용 기술:**
                * **주요 구현 내용 및 성과:**
                - [구현 기능]
                - [트러블슈팅 또는 성능 개선 경험]

                ## 학력 (Education)
                * **[학교명]** | [전공명] ([졸업/졸업예정/수료])
                - 재학 기간: YYYY.MM ~ YYYY.MM

                ## 활동 (Activities & Experience)
                * **[활동명 / 대외활동 / 교육과정]** | [역할 또는 수료 내용] (YYYY.MM ~ YYYY.MM)
                - 주요 활동 내용

                ### [출력 포맷]
                JSON 객체 하나만 반환해. 코드 블록과 JSON 밖 설명은 쓰지 마.
                서버가 id, user_id, created_at, updated_at, last_saved_at을 채우므로 포함하지 마.

                {
                  "resumeContent": "마크다운으로 정리된 전체 이력서",
                  "resumeFileName": "%s"
                }

                현재 시점: %s
                """.formatted(buildResumeFileName(originalFilename), nowText());
    }

    private String buildJobDescriptionPrompt(String jobPostingUrl, String jobPostingText, String platform) {
        // 채용공고 URL/이미지/텍스트를 마크다운 JSON으로 정리하기 위한 프롬프트 생성
        return """
                너는 채용공고 분석 전문가야. 내가 제공하는 채용공고(이미지 또는 URL 내 텍스트)를 분석해서 아래의 [작성 규칙]과 [출력 포맷(JSON)]에 맞추어 완벽한 JSON 형식으로 변환해줘.

                ### [작성 규칙]
                1. 항목 누락 방지: 공고에 존재한다면 반드시 '담당업무', '자격요건', '우대사항', '기술스택' 등의 항목을 구분하여 작성할 것.
                2. 텍스트 간소화: 긴 줄글이나 불필요한 미사여구는 배제하고, 핵심 키워드 중심의 개조식으로 명확하게 정리할 것.
                3. 데이터 추출: 공고 텍스트에서 회사명(companyName), 포지션명(positionTitle), 채용 플랫폼 출처(jobPlatform)를 정확히 추출할 것.
                4. JSON 에스케이프 규칙: jdContent 내부의 줄바꿈과 쌍따옴표는 JSON 문법에 맞게 처리할 것.

                ### [마크다운 템플릿]
                ### [[포지션명]] [회사명]

                #### 담당업무
                - [업무 내용]

                #### 자격요건
                - [요건]

                #### 우대사항
                - [우대 사항]

                #### 기술스택
                - [기술]

                ### [출력 포맷]
                JSON 객체 하나만 반환해. 코드 블록과 JSON 밖 설명은 쓰지 마.
                서버가 id, user_id, created_at, updated_at, last_saved_at을 채우므로 포함하지 마.

                {
                  "companyName": "추출된 회사명",
                  "positionTitle": "추출된 포지션명",
                  "jobPlatform": "%s",
                  "jdContent": "마크다운으로 정리된 전체 채용공고"
                }

                현재 시점: %s
                채용공고 URL: %s
                채용공고 텍스트:
                %s
                """.formatted(platform, nowText(), defaultIfBlank(jobPostingUrl, "없음"), jobPostingText);
    }

    private String buildAnalysisPrompt(String resumeContent, String jdContent) {
        // 최초 분석용 프롬프트에 기준표와 피드백 예시를 주입
        String criteriaTable = readAnalysisPromptReference("판정_기준표");
        String feedbackExamples = readAnalysisPromptReference("피드백_예시");

        return """
                # ============================================
                # ResuFit 분석용 프롬프트
                # ============================================
                # 사용법: 빈칸 4개를 실제 데이터로 교체한 후 LLM에 전송
                #   {{판정*기준표}} → 참고자료.json의 "판정*기준표" 전체
                #   {{피드백*예시}} → 참고자료.json의 "피드백*예시"에서 공고 키워드에 맞는 것 3~5개
                #   {{공고_텍스트}} → 유저가 입력한 채용공고
                #   {{이력서_텍스트}} → 유저가 올린 이력서에서 추출한 텍스트
                #
                # LLM 설정:
                #   모델: Gemini Flash 3.5
                #   temperature: 0
                #   max_tokens: 4000
                # ============================================

                [시스템 프롬프트]

                당신은 채용공고와 이력서를 대조 분석하는 전문 도구입니다.
                공고의 요구사항이 이력서에 있는지 없는지를 정확하게 찾아내고,
                이력서 표현을 공고에 맞게 개선하는 제안을 합니다.
                판단이나 의견이 아닌, 두 텍스트의 대조 결과와 사실만 제공하세요.
                반드시 JSON만 출력하세요. JSON 외의 텍스트, 마크다운 백틱, 설명을 포함하지 마세요.

                [유저 프롬프트]

                아래 [채용공고]와 [이력서]를 대조 분석하세요.

                ━━━━━━━━━━━━━━━━━━━━
                [STEP 1] 요건 추출
                ━━━━━━━━━━━━━━━━━━━━

                채용공고에서 요건을 항목별로 추출하고, 2가지로 분류하세요.

                ■ required (필수요건)
                - "자격요건", "필수", "이상", "경력", "지원자격" 근처에 있는 항목
                - "담당업무", "주요업무"에서 도출되는 핵심 역량
                - 미충족 시 서류 탈락 가능성이 높은 항목

                ■ preferred (우대사항)
                - "우대", "플러스", "있으면 좋은" 근처에 있는 항목
                - 없어도 서류 통과는 가능하지만 경쟁력이 되는 항목

                항목 추출 원칙:
                1. 하나의 판단 가능한 단위로 쪼갠다
                   - "React, TypeScript 경험" → 2개로 분리
                   - "백엔드 개발 3년 이상" → 1개 유지 (경력 연차는 쪼개지 않음)
                   - "Git, Jira 등 협업 도구" → 1개 유지 (유사 도구 묶음은 쪼개지 않음)
                2. 이력서로 판단 불가능한 항목은 제외:
                   - 인성 ("성실한 분", "커뮤니케이션 능력")
                   - 근무 조건 ("연봉 협의", "서울 근무")
                   - 복리후생 ("스톡옵션", "식대")
                3. 공고에 없는 요건을 추가하지 마세요

                ━━━━━━━━━━━━━━━━━━━━
                [STEP 2] 매칭 판정
                ━━━━━━━━━━━━━━━━━━━━

                각 요건을 이력서와 대조하여 3단계로 판정하세요.

                ■ met (확인됨 ✅)
                  이력서에서 해당 요건과 직접 관련된 경험·기술이 확인됨.
                  키워드와 실무 맥락이 모두 일치.

                ■ partial (보강 필요 🟡)
                  관련 내용이 있으나 아래 중 하나에 해당:
                - 키워드는 있지만 구체성·깊이 부족
                - 유사 기술은 있으나 정확히 일치하지 않음
                - 경력 연차가 요구 대비 부족
                - 기술명이 나열만 되어 있고 실무 맥락 없음

                ■ gap (없음 🔴)
                  이력서 전체에서 관련 키워드·경험·기술을 찾을 수 없음.

                ⚠️ 판정이 애매할 때는 보수적으로 판정하세요.
                   met보다는 partial, partial보다는 gap 쪽으로.

                [상세 판정 기준표]
                %s

                ━━━━━━━━━━━━━━━━━━━━
                [STEP 3] 피드백 작성
                ━━━━━━━━━━━━━━━━━━━━

                partial(🟡) 또는 gap(🔴)인 항목에만 피드백을 작성하세요.
                met(✅)인 항목에는 피드백을 작성하지 마세요.

                각 피드백에 아래 3가지를 포함하세요:

                1. evidence (이력서 근거 — 짧게)
                   - partial일 때: 이력서에서 관련 부분을 찾았으면 "이력서 근거: ~" 형태로 짧게 인용
                     예: "이력서 근거: MySQL, JPA 사용 언급 확인"
                     예: "이력서 근거: 기술 스택에 'GitHub Actions' 언급 확인"
                     예: "이력서 근거: Florent '꽃 역경매 마켓플레이스' 확인"
                   - gap일 때: null (이력서에 관련 내용이 아예 없으므로)
                   - ⚠️ 길게 쓰지 마세요. "이력서 근거: ~" 한 줄이면 충분합니다.

                2. feedback (메인 피드백 — 대조 결과 + 중요한 이유를 한 문장으로)
                   - 공고가 뭘 요구하는데 이력서가 어떤 상태인지 + 왜 중요한지를 합쳐서 1~2문장으로 쓰세요.
                   - 예 (gap): "공고에서 Java/Spring Boot 경험을 필수로 요구하지만, 이력서 전체에서 관련 내용을 찾을 수 없습니다."
                   - 예 (partial): "RDBMS 경험이 필수인데, 사용 기술로만 나열되어 있고 설계/최적화 경험이 드러나지 않습니다."
                   - 예 (partial): "마켓플레이스 경험이 커머스와 연결될 수 있지만 현재 표현이 약합니다."
                   - ⚠️ 이력서에 없는 내용을 있는 것처럼 쓰지 마세요.

                3. suggestion (수정 제안 — 💡이렇게 보완해보세요)
                   - 이력서를 어떻게 고치면 공고 표현에 맞아지는지 구체적 가이드
                   - 유저가 복사해서 바로 쓸 수 있는 수준의 예시 문장 포함
                   - gap이어도 작성 — "~경험이 있다면 기술 스택에 추가하고, ~등을 기술하세요" 형태로
                   - ⚠️ 없는 경험을 있는 것처럼 쓰라고 유도하지 마세요

                [이 공고와 관련된 좋은 피드백 예시]
                %s

                ━━━━━━━━━━━━━━━━━━━━
                [STEP 4] Red/Yellow Flag 분류
                ━━━━━━━━━━━━━━━━━━━━

                ■ Red flag (🔴)
                필수요건(required) 중 gap인 항목.
                  = 공고가 "필수"라고 적은 건데, 이력서에 관련 내용이 아예 없음.

                ■ Yellow flag (🟡)
                아래 중 하나에 해당:
                - 필수요건(required) 중 partial인 항목
                - 우대사항(preferred) 중 partial 또는 gap인 항목
                  = 관련 내용은 있지만 공고 표현과 맞지 않거나, 우대사항을 놓치고 있음.

                ━━━━━━━━━━━━━━━━━━━━
                [환각 방지 규칙]
                ━━━━━━━━━━━━━━━━━━━━

                - 이력서에 없는 경험을 추론하지 마세요. 없으면 gap.
                - evidence는 이력서 원문을 변형 없이 인용하세요.
                - suggestion에서 없는 경험을 있는 것처럼 쓰지 마세요.
                - 공고에 없는 요건을 추가하지 마세요.
                - met인 항목에 피드백을 쓰지 마세요.

                ━━━━━━━━━━━━━━━━━━━━

                [채용공고]
                %s

                [이력서]
                %s

                ━━━━━━━━━━━━━━━━━━━━
                [출력 형식]
                ━━━━━━━━━━━━━━━━━━━━

                {
                  "company": "회사명",
                  "position": "포지션명",
                  "summary": {
                    "met_count": 0,
                    "partial_count": 0,
                    "gap_count": 0,
                    "red_flag_count": 0,
                    "yellow_flag_count": 0,
                    "top_message": "가장 먼저 보강해야 할 한 줄 요약"
                  },
                  "requirements": [
                    {
                      "id": "req_1",
                      "text": "공고 원문 표현 그대로",
                      "type": "required | preferred",
                      "status": "met | partial | gap",
                      "flag": null | "red" | "yellow",
                      "evidence": "이력서 근거: ~ (partial일 때만. gap이면 null)",
                      "feedback": "대조 결과 + 중요한 이유 합친 1~2문장 | null",
                      "suggestion": "수정 제안 + 복사 가능한 예시 문장 | null"
                    }
                  ]
                }
                """.formatted(criteriaTable, feedbackExamples, jdContent, resumeContent);
    }

    private String buildReanalysisPrompt(String resumeContent, String jdContent) {
        // 재분석용 프롬프트에 기준표와 피드백 예시를 주입
        String criteriaTable = readAnalysisPromptReference("판정_기준표");
        String feedbackExamples = readAnalysisPromptReference("피드백_예시");
        String fixedRequirements = "";

        return """
                # ============================================
                # ResuFit 재분석용 프롬프트
                # ============================================
                # 사용법: 유저가 이력서 수정 후 "재분석" 클릭할 때 사용
                #   빈칸 4개를 실제 데이터로 교체한 후 LLM에 전송
                #   {{판정_기준표}} → 참고자료.json의 "판정_기준표" 전체
                #   {{피드백_예시}} → 첫 분석 때와 동일한 것 사용
                #   {{고정_요건_목록}} → DB에 저장된 첫 분석의 requirements (id, text, type만)
                #   {{수정된_이력서}} → 유저가 수정한 이력서 텍스트
                #
                # ⚠️ 주의: 재분석 최대 5회. 5회 초과 시 이 프롬프트 호출하지 말 것.
                #
                # LLM 설정: 분석용과 동일
                #   모델: Gemini Flash 3.5
                #   temperature: 0
                #   max_tokens: 4000
                # ============================================


                [시스템 프롬프트]

                당신은 채용공고와 이력서를 대조 분석하는 전문 도구입니다.
                아래 [고정된 요건 목록]의 각 항목을 [수정된 이력서]와 대조하여 매칭 판정과 피드백을 재작성하세요.
                판단이나 의견이 아닌, 두 텍스트의 대조 결과와 사실만 제공하세요.
                반드시 JSON만 출력하세요. JSON 외의 텍스트, 마크다운 백틱, 설명을 포함하지 마세요.


                [유저 프롬프트]

                ━━━━━━━━━━━━━━━━━━━━
                [주의] 요건 목록은 고정입니다
                ━━━━━━━━━━━━━━━━━━━━

                아래 요건 목록의 id, text, type은 절대 변경하지 마세요.
                요건을 추가하거나 삭제하지 마세요.
                각 요건의 status, flag, evidence, feedback, suggestion만 재판정하세요.

                매칭 판정 기준 (met/partial/gap), 피드백 작성 규칙, flag 분류 규칙은
                분석용 프롬프트와 동일하게 적용하세요.

                [판정 기준표]
                %s

                [피드백 예시]
                %s

                [고정된 요건 목록]
                %s

                [수정된 이력서]
                %s

                [원본 공고]
                %s

                ━━━━━━━━━━━━━━━━━━━━
                [출력 형식]
                ━━━━━━━━━━━━━━━━━━━━

                분석용 프롬프트와 동일한 JSON 구조로 출력하세요.
                summary의 모든 카운트도 재산출하세요.

                {
                  "company": "회사명",
                  "position": "포지션명",
                  "summary": {
                    "met_count": 0,
                    "partial_count": 0,
                    "gap_count": 0,
                    "red_flag_count": 0,
                    "yellow_flag_count": 0,
                    "top_message": "가장 먼저 보강해야 할 한 줄 요약"
                  },
                  "requirements": [
                    {
                      "id": "req_1",
                      "text": "공고 원문 표현 그대로",
                      "type": "required | preferred",
                      "status": "met | partial | gap",
                      "flag": null | "red" | "yellow",
                      "evidence": "이력서 근거: ~ (partial일 때만. gap이면 null)",
                      "feedback": "대조 결과 + 중요한 이유 합친 1~2문장 | null",
                      "suggestion": "수정 제안 + 복사 가능한 예시 문장 | null"
                    }
                  ]
                }
                """.formatted(criteriaTable, feedbackExamples, fixedRequirements, resumeContent, jdContent);
    }

    private String readAnalysisPromptReference(String fieldName) {
        try {
            // 참고자료 JSON에서 필요한 최상위 필드만 추출
            String references = Files.readString(ANALYSIS_PROMPT_REFERENCES_PATH, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(references);
            return root.required(fieldName).toString();
        } catch (IOException | JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private record CountResult(
            int redCount,
            int yellowCount,
            int greenCount
    ) {
    }
}
