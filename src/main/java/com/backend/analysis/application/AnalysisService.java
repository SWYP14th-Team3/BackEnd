package com.backend.analysis.application;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.Satisfaction;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.dto.GeminiResumeResponse;
import com.backend.analysis.dto.response.AnalysisDeleteResponse;
import com.backend.analysis.dto.response.AnalysisDetailResponse;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
import com.backend.analysis.dto.response.JobRequirementResponse;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.analysis.infrastructure.JobDescriptionRepository;
import com.backend.analysis.infrastructure.JobRequirementRepository;
import com.backend.analysis.infrastructure.RequirementEvaluationRepository;
import com.backend.analysis.infrastructure.UserResumeRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final JobPostingCrawler jobPostingCrawler;
    private final GeminiAnalysisClient geminiAnalysisClient;
    private final UserRepository userRepository;
    private final UserResumeRepository userResumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final RequirementEvaluationRepository requirementEvaluationRepository;

    @Transactional
    public AnalysisDetailResponse createAnalysis(
            Long userId,
            String jobPostingUrl,
            String jobPostingText,
            MultipartFile jobPostingImage,
            MultipartFile resumePdf
    ) {
        validatePdf(resumePdf);
        validateJobPostingInput(jobPostingUrl, jobPostingText, jobPostingImage);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        GeminiResumeResponse resumeResponse = geminiAnalysisClient.summarizeResume(
                resumePdf,
                buildResumePrompt(resumePdf.getOriginalFilename())
        );
        UserResume resume = userResumeRepository.save(
                UserResume.builder()
                        .user(user)
                        .resumeContent(resumeResponse.resumeContent())
                        .resumeFileName(defaultIfBlank(
                                resumeResponse.resumeFileName(),
                                buildResumeFileName(resumePdf.getOriginalFilename())
                        ))
                        .build()
        );

        String crawledText = crawlJobPostingText(jobPostingUrl, jobPostingText);
        String platform = jobPostingCrawler.extractPlatform(jobPostingUrl);
        GeminiJobDescriptionResponse jobDescriptionResponse = geminiAnalysisClient.summarizeJobDescription(
                buildJobDescriptionPrompt(jobPostingUrl, crawledText, platform),
                jobPostingImage
        );
        JobDescription jobDescription = jobDescriptionRepository.save(
                JobDescription.builder()
                        .user(user)
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
        AnalysisResult analysisResult = analysisResultRepository.save(
                AnalysisResult.builder()
                        .user(user)
                        .userResume(resume)
                        .jobDescription(jobDescription)
                        .overallLevel(parseOverallLevel(analysisResponse.overallLevel()))
                        .redCount(countResult.redCount())
                        .yellowCount(countResult.yellowCount())
                        .greenCount(countResult.greenCount())
                        .build()
        );

        List<JobRequirementResponse> requirements = saveRequirementsAndEvaluations(
                analysisResult,
                analysisResponse.requirements()
        );

        return AnalysisDetailResponse.from(analysisResult, requirements);
    }

    @Transactional
    public AnalysisSaveResponse saveResume(Long userId, Long analysisResultId, String resumeCurrentText) {
        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        LocalDateTime savedAt = LocalDateTime.now();
        analysisResult.getUserResume().updateResumeContent(resumeCurrentText, savedAt);
        analysisResult.markSaved(savedAt);
        analysisResultRepository.flush();

        return AnalysisSaveResponse.from(analysisResult);
    }

    @Transactional
    public AnalysisSatisfactionResponse updateSatisfaction(Long userId, Long analysisResultId, String satisfactionValue) {
        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        analysisResult.updateSatisfaction(parseSatisfaction(satisfactionValue));
        analysisResultRepository.flush();

        return AnalysisSatisfactionResponse.from(analysisResult);
    }

    @Transactional
    public AnalysisDeleteResponse deleteAnalysisResult(Long userId, Long analysisResultId) {
        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        analysisResult.delete(LocalDateTime.now());
        analysisResultRepository.flush();

        return AnalysisDeleteResponse.from(analysisResult);
    }

    private AnalysisResult findOwnedAnalysisResult(Long userId, Long analysisResultId) {
        AnalysisResult analysisResult = analysisResultRepository.findByIdAndDeletedAtIsNull(analysisResultId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

        if (!analysisResult.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ANALYSIS_RESULT_FORBIDDEN);
        }

        return analysisResult;
    }

    private List<JobRequirementResponse> saveRequirementsAndEvaluations(
            AnalysisResult analysisResult,
            List<GeminiRequirementResult> requirementResults
    ) {
        List<JobRequirementResponse> responses = new ArrayList<>();

        for (GeminiRequirementResult item : requirementResults) {
            MatchStatus matchStatus = parseMatchStatus(item.matchStatus());
            JobRequirement requirement = jobRequirementRepository.save(
                    JobRequirement.builder()
                            .analysisResult(analysisResult)
                            .category(parseRequirementCategory(item.category()))
                            .title(defaultIfBlank(item.title(), "요건"))
                            .description(defaultIfBlank(item.description(), item.title()))
                            .sourceText(item.sourceText())
                            .build()
            );

            RequirementEvaluation evaluation = requirementEvaluationRepository.save(
                    RequirementEvaluation.builder()
                            .jobRequirement(requirement)
                            .matchStatus(matchStatus)
                            .resumeEvidence(normalizeEvaluationText(matchStatus, item.resumeEvidence()))
                            .feedback(normalizeEvaluationText(matchStatus, item.feedback()))
                            .revisionSuggestion(normalizeEvaluationText(matchStatus, item.revisionSuggestion()))
                            .build()
            );

            responses.add(JobRequirementResponse.from(requirement, evaluation));
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

    private void validateAnalysisResponse(GeminiAnalysisResponse analysisResponse) {
        if (analysisResponse == null
                || analysisResponse.requirements() == null
                || analysisResponse.requirements().isEmpty()) {
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }
    }

    private String crawlJobPostingText(String jobPostingUrl, String jobPostingText) {
        if (jobPostingText != null && !jobPostingText.isBlank()) {
            return jobPostingText.trim();
        }

        if (jobPostingUrl != null && !jobPostingUrl.isBlank()) {
            return jobPostingCrawler.extractText(jobPostingUrl.trim());
        }

        return "";
    }

    private CountResult countStatus(List<GeminiRequirementResult> requirements) {
        int red = 0;
        int yellow = 0;
        int green = 0;

        for (GeminiRequirementResult requirement : requirements) {
            MatchStatus status = parseMatchStatus(requirement.matchStatus());

            if (status == MatchStatus.MISSING) {
                red++;
            } else if (status == MatchStatus.NEEDS_IMPROVEMENT) {
                yellow++;
            } else if (status == MatchStatus.CONFIRMED) {
                green++;
            }
        }

        return new CountResult(red, yellow, green);
    }

    private RequirementCategory parseRequirementCategory(String category) {
        if ("preferred".equalsIgnoreCase(category)
                || "PREFERRED".equalsIgnoreCase(category)
                || "우대사항".equals(category)) {
            return RequirementCategory.PREFERENCE;
        }

        return RequirementCategory.QUALIFICATION;
    }

    private MatchStatus parseMatchStatus(String status) {
        if ("met".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status)
                || "확인됨".equals(status)) {
            return MatchStatus.CONFIRMED;
        }

        if ("gap".equalsIgnoreCase(status)
                || "MISSING".equalsIgnoreCase(status)
                || "없음".equals(status)) {
            return MatchStatus.MISSING;
        }

        return MatchStatus.NEEDS_IMPROVEMENT;
    }

    private OverallLevel parseOverallLevel(String level) {
        if ("HIGH".equalsIgnoreCase(level)) {
            return OverallLevel.HIGH;
        }
        if ("LOW".equalsIgnoreCase(level)) {
            return OverallLevel.LOW;
        }

        return OverallLevel.MEDIUM;
    }

    private Satisfaction parseSatisfaction(String satisfactionValue) {
        return switch (satisfactionValue.trim()) {
            case "LIKE" -> Satisfaction.LIKE;
            case "DISLIKE" -> Satisfaction.DISLIKE;
            case "NULL" -> null;
            default -> throw new CustomException(ErrorCode.INVALID_ANALYSIS_SATISFACTION);
        };
    }

    private String normalizeEvaluationText(MatchStatus matchStatus, String value) {
        if (matchStatus == MatchStatus.CONFIRMED) {
            return null;
        }

        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }

        return value;
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    private String buildResumeFileName(String originalFilename) {
        String source = defaultIfBlank(originalFilename, "resume.pdf");
        String normalized = source.replaceAll("(?i)\\.pdf$", "");
        return normalized + "_정리본.md";
    }

    private String nowText() {
        return OffsetDateTime.now(ZoneOffset.ofHours(9)).toString();
    }

    private String buildResumePrompt(String originalFilename) {
        return """
                주어진 이력서 PDF를 읽고 개발자 이력서 분석에 필요한 텍스트로 정리해줘.
                JSON 객체 하나만 반환하고 코드블록이나 설명은 쓰지 마.

                작성 규칙:
                - 기술스택, 프로젝트, 경력, 학력, 활동을 마크다운으로 구분한다.
                - 프로젝트는 기간, 역할, 기술, 성과를 포함한다.
                - 깨진 줄바꿈은 자연스럽게 정리한다.

                출력 JSON:
                {
                  "resumeContent": "마크다운으로 정리된 전체 이력서",
                  "resumeFileName": "%s"
                }

                현재 시점: %s
                """.formatted(buildResumeFileName(originalFilename), nowText());
    }

    private String buildJobDescriptionPrompt(String jobPostingUrl, String jobPostingText, String platform) {
        return """
                채용공고 URL 텍스트 또는 이미지를 분석해서 회사명, 포지션명, 공고 내용을 정리해줘.
                JSON 객체 하나만 반환하고 코드블록이나 설명은 쓰지 마.

                출력 JSON:
                {
                  "companyName": "추출된 회사명",
                  "positionTitle": "추출된 포지션명",
                  "jobPlatform": "%s",
                  "jdContent": "담당업무, 자격요건, 우대사항, 기술스택을 포함한 마크다운 공고"
                }

                현재 시점: %s
                채용공고 URL: %s
                채용공고 텍스트:
                %s
                """.formatted(platform, nowText(), defaultIfBlank(jobPostingUrl, "없음"), jobPostingText);
    }

    private String buildAnalysisPrompt(String resumeContent, String jdContent) {
        return """
                너는 고도화된 IT 전문 커리어 코치이자 채용 담당자야.
                아래 [지원자 이력서]와 [채용공고]를 비교해서 공고 요건별 적합도를 분석해줘.
                반드시 JSON 객체 하나만 반환하고 코드블록이나 JSON 밖 설명은 쓰지 마.

                판정 기준:
                - met: 이력서에서 요건과 직접 관련된 기술/경험/성과가 확인됨
                - partial: 관련 경험은 있으나 구체성, 깊이, 정확한 키워드, 연차가 부족함
                - gap: 이력서에서 관련 근거를 찾을 수 없음

                요건 분류:
                - required: 자격요건, 필수요건, 담당업무에서 도출되는 핵심 역량
                - preferred: 우대사항

                feedback 작성 규칙:
                - partial/gap 항목에는 이력서 근거, 진단 이유, 보완 제안을 구체적으로 작성한다.
                - met 항목은 evidence, feedback, suggestion을 null로 둔다.
                - 이력서에 없는 경험을 있다고 꾸며내지 않는다.

                출력 JSON:
                {
                  "company": "회사명",
                  "position": "포지션명",
                  "summary": {
                    "met_count": 0,
                    "partial_count": 0,
                    "gap_count": 0,
                    "red_flag_count": 0,
                    "yellow_flag_count": 0,
                    "top_message": "전체 요약"
                  },
                  "requirements": [
                    {
                      "id": "REQ-001",
                      "text": "요건명",
                      "type": "required | preferred",
                      "status": "met | partial | gap",
                      "flag": "red | yellow | null",
                      "evidence": "이력서 근거 | null",
                      "feedback": "진단 이유 | null",
                      "suggestion": "복사 가능한 수정 제안 | null"
                    }
                  ]
                }

                [채용공고]
                %s

                [지원자 이력서]
                %s
                """.formatted(jdContent, resumeContent);
    }

    private record CountResult(
            int redCount,
            int yellowCount,
            int greenCount
    ) {
    }
}
