package com.backend.analysis_20260710.service;

import com.backend.analysis_20260710.client.GeminiAnalysisClient;
import com.backend.analysis_20260710.client.JobPostingCrawler;
import com.backend.analysis_20260710.domain.AnalysisResult;
import com.backend.analysis_20260710.domain.JobRequirement;
import com.backend.analysis_20260710.domain.RequirementEvaluation;
import com.backend.analysis_20260710.dto.AnalysisResponse;
import com.backend.analysis_20260710.dto.GeminiAnalysisResponse;
import com.backend.analysis_20260710.dto.GeminiRequirementResult;
import com.backend.analysis_20260710.dto.RequirementResponse;
import com.backend.analysis_20260710.repository.AnalysisResultRepository;
import com.backend.analysis_20260710.repository.JobRequirementRepository;
import com.backend.analysis_20260710.repository.RequirementEvaluationRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnalysisService {

    private final JobPostingCrawler jobPostingCrawler;
    private final GeminiAnalysisClient geminiAnalysisClient;
    private final AnalysisResultRepository analysisResultRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final RequirementEvaluationRepository requirementEvaluationRepository;

    public AnalysisService(
            JobPostingCrawler jobPostingCrawler,
            GeminiAnalysisClient geminiAnalysisClient,
            AnalysisResultRepository analysisResultRepository,
            JobRequirementRepository jobRequirementRepository,
            RequirementEvaluationRepository requirementEvaluationRepository
    ) {
        this.jobPostingCrawler = jobPostingCrawler;
        this.geminiAnalysisClient = geminiAnalysisClient;
        this.analysisResultRepository = analysisResultRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.requirementEvaluationRepository = requirementEvaluationRepository;
    }

    @Transactional
    public AnalysisResponse analyze(
            String userId,
            String jobPostingMode,
            String jobUrl,
            String jobPostingText,
            MultipartFile resumePdf
    ) {
        validatePdf(resumePdf); // pdf검증

        String normalizedMode = normalizeJobPostingMode(jobPostingMode);
        String jobPostingRaw = extractJobPostingRaw(normalizedMode, jobUrl, jobPostingText);
        String jobPlatform = "URL".equals(normalizedMode)
                ? jobPostingCrawler.extractPlatform(jobUrl)
                : "TEXT";
        String prompt = buildPrompt(jobPostingRaw);

        GeminiAnalysisResponse geminiResponse = geminiAnalysisClient.analyze(resumePdf, prompt);
        CountResult countResult = countStatus(geminiResponse.requirements());

        AnalysisResult result = analysisResultRepository.save(
                AnalysisResult.builder()
                        .userId(userId)
                        .jobInputType(normalizedMode)
                        .jobUrl(jobUrl)
                        .jobPlatform(jobPlatform)
                        .jobPostingRaw(jobPostingRaw)
                        .resumeOriginalText(geminiResponse.resumeOriginalText())
                        .resumeCurrentText(geminiResponse.resumeOriginalText())
                        .companyName(geminiResponse.companyName())
                        .positionTitle(geminiResponse.positionTitle())
                        .overallLevel(normalizeOverallLevel(geminiResponse.overallLevel()))
                        .redCount(countResult.redCount())
                        .yellowCount(countResult.yellowCount())
                        .greenCount(countResult.greenCount())
                        .build()
        );

        List<RequirementEvaluation> evaluations = saveRequirementsAndEvaluations(
                result,
                geminiResponse.requirements()
        );

        List<RequirementResponse> requirementResponses = evaluations.stream()
                .map(RequirementResponse::from)
                .toList();

        return AnalysisResponse.of(result, requirementResponses);
    }

    private List<RequirementEvaluation> saveRequirementsAndEvaluations(
            AnalysisResult result,
            List<GeminiRequirementResult> requirementResults
    ) {
        if (requirementResults == null) {
            return List.of();
        }

        List<RequirementEvaluation> evaluations = new ArrayList<>();

        for (GeminiRequirementResult item : requirementResults) {
            JobRequirement requirement = jobRequirementRepository.save(
                    JobRequirement.builder()
                            .analysisResult(result)
                            .category(normalizeCategory(item.category()))
                            .title(item.title())
                            .description(item.description())
                            .sourceText(item.sourceText())
                            .build()
            );

            RequirementEvaluation evaluation = requirementEvaluationRepository.save(
                    RequirementEvaluation.builder()
                            .requirement(requirement)
                            .matchStatus(normalizeMatchStatus(item.matchStatus()))
                            .resumeEvidence(item.resumeEvidence())
                            .feedback(item.feedback())
                            .revisionSuggestion(item.revisionSuggestion())
                            .build()
            );

            evaluations.add(evaluation);
        }

        return evaluations;
    }

    private void validatePdf(MultipartFile resumePdf) {
        if (resumePdf == null || resumePdf.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }

        if (!"application/pdf".equals(resumePdf.getContentType())) {
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }
    }

    private String normalizeJobPostingMode(String jobPostingMode) {
        if ("text".equalsIgnoreCase(jobPostingMode) || "TEXT".equalsIgnoreCase(jobPostingMode)) {
            return "TEXT";
        }

        return "URL";
    }

    private String extractJobPostingRaw(String jobPostingMode, String jobUrl, String jobPostingText) {
        if ("TEXT".equals(jobPostingMode)) {
            if (jobPostingText == null || jobPostingText.isBlank()) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }

            return jobPostingText.trim();
        }

        if (jobUrl == null || jobUrl.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return jobPostingCrawler.extractText(jobUrl.trim());
    }

    private CountResult countStatus(List<GeminiRequirementResult> requirements) {
        int red = 0;
        int yellow = 0;
        int green = 0;

        if (requirements == null) {
            return new CountResult(red, yellow, green);
        }

        for (GeminiRequirementResult requirement : requirements) {
            String status = normalizeMatchStatus(requirement.matchStatus());

            if ("MISSING".equals(status)) {
                red++;
            } else if ("NEEDS_IMPROVEMENT".equals(status)) {
                yellow++;
            } else if ("CONFIRMED".equals(status)) {
                green++;
            }
        }

        return new CountResult(red, yellow, green);
    }

    private String normalizeCategory(String category) {
        if ("REQUIRED".equals(category)
                || "WORK_SKILL".equals(category)
                || "DOMAIN".equals(category)
                || "PREFERRED".equals(category)) {
            return category;
        }

        return "REQUIRED";
    }

    private String normalizeMatchStatus(String status) {
        if ("CONFIRMED".equals(status)
                || "NEEDS_IMPROVEMENT".equals(status)
                || "MISSING".equals(status)) {
            return status;
        }

        return "NEEDS_IMPROVEMENT";
    }

    private String normalizeOverallLevel(String level) {
        if ("HIGH".equals(level)
                || "MEDIUM".equals(level)
                || "LOW".equals(level)) {
            return level;
        }

        return "MEDIUM";
    }
    private String buildPrompt(String jobPostingRaw) {
        return """
너는 고도화된 IT 전문 커리어 코치이자 채용 담당자야.

내가 제공하는 [지원자 이력서 PDF]와 [채용공고 원문]을 비교 분석해서,
채용공고 핏(Fit) 분석 결과를 아래 JSON 형식으로만 반환해.

중요 규칙:
- JSON 외의 설명 문장은 절대 쓰지 마.
- Markdown 코드블록도 쓰지 마.
- 응답은 반드시 { 로 시작해서 } 로 끝나야 해.
- 이력서에 없는 내용은 절대 지어내지 마.
- 채용공고의 필수요건과 우대사항을 항목별로 분석해.
- 각 항목은 requirements 배열의 객체 1개로 저장될 수 있어야 해.
- 사용자가 화면에서 “🚀 회사명 포지션명 채용공고 핏(Fit) 분석 리포트” 형태로 보여줄 수 있도록 충분히 자세히 작성해.

진단 기준:
- CONFIRMED: 이력서에서 해당 요건을 뒷받침하는 명확한 근거가 있음
- NEEDS_IMPROVEMENT: 관련 경험은 있으나 연차, 표현, 도메인 키워드, 깊이가 부족함
- MISSING: 이력서에서 관련 근거를 찾기 어려움

category 값:
- REQUIRED: 필수요건, 자격요건
- WORK_SKILL: 업무역량, 주요업무 수행 역량
- DOMAIN: 특정 산업/도메인 경험
- PREFERRED: 우대사항

overallLevel 기준:
- HIGH: 핵심 필수요건 대부분이 CONFIRMED
- MEDIUM: 핵심 기술은 맞지만 일부 요건이 NEEDS_IMPROVEMENT 또는 MISSING
- LOW: 핵심 필수요건 다수가 MISSING

반환 JSON 형식:

{
  "companyName": "회사명",
  "positionTitle": "포지션명",
  "overallLevel": "HIGH 또는 MEDIUM 또는 LOW",
  "resumeOriginalText": "PDF 이력서에서 추출한 핵심 텍스트",
  "requirements": [
    {
      "category": "REQUIRED 또는 WORK_SKILL 또는 DOMAIN 또는 PREFERRED",
      "title": "요건명",
      "description": "요건 설명",
      "sourceText": "채용공고에서 해당 요건을 판단한 원문 근거",
      "matchStatus": "CONFIRMED 또는 NEEDS_IMPROVEMENT 또는 MISSING",
      "resumeEvidence": "이력서 근거. 이력서에서 확인된 기술 스택, 프로젝트, 경력, 성과를 구체적으로 작성",
      "feedback": "진단 이유. 왜 완전 충족/부분 보완/없음인지 채용 담당자 관점에서 상세히 설명",
      "revisionSuggestion": "이력서에 바로 추가하거나 수정할 수 있는 문장 형태의 제안"
    }
  ]
}

작성 방식:
1. requirements 배열에는 필수요건과 우대사항을 모두 넣어.
2. 필수요건은 category를 REQUIRED로 넣어.
3. 우대사항은 category를 PREFERRED로 넣어.
4. 업무역량 성격이 강한 항목은 WORK_SKILL로 넣어.
5. HR, 커머스, 금융, 물류 같은 산업 경험은 DOMAIN으로 넣어.
6. resumeEvidence는 “이력서 근거:”에 들어갈 문장처럼 자세히 작성해.
7. feedback은 “진단 이유:”에 들어갈 문장처럼 자세히 작성해.
8. revisionSuggestion은 “이렇게 보완해보세요 / 수정 제안:”에 들어갈 문장처럼 작성해.
9. revisionSuggestion에는 실제 이력서에 넣을 수 있는 문장 예시를 포함해.
10. redCount, yellowCount, greenCount는 서버에서 계산하므로 JSON에 포함하지 마.
11. id, user_id, analysis_result_id, requirement_id, created_at, updated_at, last_saved_at, deleted_at은 서버에서 넣을 값이므로 JSON에 포함하지 마.

화면 출력용 문체:
- resumeEvidence, feedback, revisionSuggestion은 사용자가 보여준 리포트 예시처럼 전문적이고 구체적으로 작성해.
- “실무 적합성은 높으나”, “서류 통과를 위해서는”, “핵심 연결고리가 될 수 있으며” 같은 채용 코치 문체를 사용해.
- 부족한 부분은 비난하지 말고 보완 전략 중심으로 작성해.

[채용공고 원문]
%s
                """.formatted(jobPostingRaw);
    }
    private String buildPrompt01(String jobPostingRaw) {
        return """
                너는 고도화된 IT 전문 커리어 코치이자 채용 담당자야.

                [지원자 이력서 PDF]와 [채용공고 원문]을 비교 분석해줘.

                category는 REQUIRED, WORK_SKILL, DOMAIN, PREFERRED 중 하나만 사용해.
                matchStatus는 CONFIRMED, NEEDS_IMPROVEMENT, MISSING 중 하나만 사용해.

                각 요건은 반드시 아래 필드를 포함해야 해.
                - resumeEvidence: 이력서에서 확인된 근거
                - feedback: 충족/보강/없음 판단 이유
                - revisionSuggestion: 이력서에 추가하거나 고칠 문장 제안

                반드시 JSON만 반환해.
                Markdown 코드블록은 쓰지 마.
                JSON 밖에 설명 문장을 쓰지 마.
                이력서에 없는 내용은 지어내지 마.

                반환 JSON 형식:
                {
                  "companyName": "회사명",
                  "positionTitle": "포지션명",
                  "overallLevel": "HIGH|MEDIUM|LOW",
                  "resumeOriginalText": "PDF 이력서에서 읽은 핵심 원문 또는 요약 텍스트",
                  "requirements": [
                    {
                      "category": "REQUIRED|WORK_SKILL|DOMAIN|PREFERRED",
                      "title": "요건명",
                      "description": "요건 설명",
                      "sourceText": "공고에서 해당 요건의 원문 근거",
                      "matchStatus": "CONFIRMED|NEEDS_IMPROVEMENT|MISSING",
                      "resumeEvidence": "이력서 근거",
                      "feedback": "상세 피드백",
                      "revisionSuggestion": "수정 제안"
                    }
                  ]
                }

                [채용공고 원문]
                %s
                """.formatted(jobPostingRaw);
    }

    private record CountResult(
            int redCount,
            int yellowCount,
            int greenCount
    ) {
    }
}
