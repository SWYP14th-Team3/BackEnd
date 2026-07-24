package com.backend.analysis.application;

import com.backend.analysis.client.GeminiAnalysisClient;
import com.backend.analysis.client.JobPostingCrawler;
import com.backend.analysis.domain.AnalysisAttempt;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobInputType;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.RequirementType;
import com.backend.analysis.domain.Satisfaction;
import com.backend.analysis.domain.UserResume;
import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiCardContentResult;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
import com.backend.analysis.dto.GeminiPriorityScoreResult;
import com.backend.analysis.dto.GeminiRequirementResult;
import com.backend.analysis.dto.response.AnalysisDeleteResponse;
import com.backend.analysis.dto.response.AnalysisDetailResponse;
import com.backend.analysis.dto.response.AnalysisFinalSaveResponse;
import com.backend.analysis.dto.response.AnalysisPageResponse;
import com.backend.analysis.dto.response.AnalysisSaveResponse;
import com.backend.analysis.dto.response.AnalysisSatisfactionResponse;
import com.backend.analysis.dto.response.AnalysisSummaryResponse;
import com.backend.analysis.dto.response.JobRequirementResponse;
import com.backend.analysis.dto.response.ReanalysisResponse;
import com.backend.analysis.infrastructure.AnalysisAttemptRepository;
import com.backend.analysis.infrastructure.AnalysisResultRepository;
import com.backend.analysis.infrastructure.JobDescriptionRepository;
import com.backend.analysis.infrastructure.JobRequirementRepository;
import com.backend.analysis.infrastructure.RequirementEvaluationRepository;
import com.backend.analysis.infrastructure.UserResumeRepository;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int PDF_HEADER_SCAN_LIMIT = 1024;
    private static final long MAX_RESUME_PDF_SIZE = 10 * 1024 * 1024;
    private static final int MAX_RETRY_COUNT = 5;

    private final JobPostingCrawler jobPostingCrawler;
    private final GeminiAnalysisClient geminiAnalysisClient;
    private final UserRepository userRepository;
    private final UserResumeRepository userResumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final AnalysisAttemptRepository analysisAttemptRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final RequirementEvaluationRepository requirementEvaluationRepository;

    @Transactional(readOnly = true)
    public AnalysisPageResponse<AnalysisSummaryResponse> getAnalyses(
            Long userId,
            int page,
            int size,
            String companyName
    ) {
        validatePageRequest(page, size);

        User user = findOrCreateTestUser(userId);

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AnalysisResult> analysisResults;

        if (companyName == null || companyName.isBlank()) {
            analysisResults = analysisResultRepository.findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(
                    user,
                    pageRequest
            );
        } else {
            analysisResults = analysisResultRepository
                    .findAllByUserAndDeletedAtIsNullAndJobDescription_CompanyNameContainingIgnoreCaseOrderByCreatedAtDesc(
                            user,
                            companyName.trim(),
                            pageRequest
                    );
        }

        return AnalysisPageResponse.from(analysisResults.map(AnalysisSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public AnalysisDetailResponse getAnalysisDetail(Long userId, Long analysisResultId) {
        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        return AnalysisDetailResponse.from(
                analysisResult,
                getRequirementResponses(analysisResult)
        );
    }

    @Transactional
    public AnalysisDetailResponse createAnalysis(
            Long userId,
            JobInputType jobInputType,
            String jobUrl,
            String jobText,
            MultipartFile resumeFile,
            List<MultipartFile> jobImages
    ) {
        validatePdf(resumeFile);
        validateJobPostingInput(jobInputType, jobUrl, jobText, jobImages);

        User user = findOrCreateTestUser(userId);

        String resumeText = extractResumeText(resumeFile);
        UserResume resume = userResumeRepository.save(
                UserResume.builder()
                        .user(user)
                        .resumeContent(resumeText)
                        .resumeFileName(buildResumeFileName(resumeFile.getOriginalFilename()))
                        .resumeFileSize(resumeFile.getSize())
                        .build()
        );

        MultipartFile jobPostingImage = firstPresentImage(jobImages);
        String crawledText = crawlJobPostingText(jobInputType, jobUrl, jobText);
        String platform = jobPostingCrawler.extractPlatform(jobUrl);
        GeminiJobDescriptionResponse jobDescriptionResponse = geminiAnalysisClient.summarizeJobDescription(
                buildJobDescriptionPrompt(jobUrl, crawledText, platform),
                jobPostingImage
        );
        validateJobDescriptionResponse(jobDescriptionResponse);

        String jobPostingRawText = jobDescriptionResponse.jdContent().trim();
        JobDescription jobDescription = jobDescriptionRepository.save(
                JobDescription.builder()
                        .user(user)
                        .jobInputType(jobInputType)
                        .jobUrl(jobInputType == JobInputType.URL ? jobUrl.trim() : null)
                        .companyName(jobDescriptionResponse.companyName())
                        .positionTitle(jobDescriptionResponse.positionTitle())
                        .jobPlatform(defaultIfBlank(jobDescriptionResponse.jobPlatform(), platform))
                        .jdOriginalText(jobPostingRawText)
                        .jdSummaryText(jobPostingRawText)
                        .build()
        );

        GeminiAnalysisResponse analysisResponse = geminiAnalysisClient.analyze(
                buildAnalysisPrompt(resume.getResumeContent(), jobDescription.getJdContent())
        );
        validateAnalysisResponse(analysisResponse);
        jobDescription.updateExtractedInfo(analysisResponse.company(), analysisResponse.position());
        Map<String, GeminiPriorityScoreResult> priorityScoreByReqId = scoreRedYellowRequirements(
                analysisResponse.requirements(),
                jobDescription.getJdContent(),
                resume.getResumeContent()
        );
        Map<String, GeminiCardContentResult> cardContentByReqId = createCardContents(
                analysisResponse.requirements(),
                priorityScoreByReqId,
                jobDescription.getJdContent(),
                resume.getResumeContent()
        );

        CountResult countResult = countStatus(analysisResponse.requirements());
        AnalysisResult analysisResult = analysisResultRepository.save(
                AnalysisResult.builder()
                        .user(user)
                        .userResume(resume)
                        .jobDescription(jobDescription)
                        .overallLevel(calculateOverallLevel(analysisResponse.requirements()))
                        .redCount(countResult.redCount())
                        .yellowCount(countResult.yellowCount())
                        .greenCount(countResult.greenCount())
                        .build()
        );

        List<JobRequirementResponse> requirements = saveRequirementsAndEvaluations(
                analysisResult,
                analysisResponse.requirements(),
                priorityScoreByReqId,
                cardContentByReqId
        );
        analysisAttemptRepository.save(AnalysisAttempt.initial(analysisResult));

        return AnalysisDetailResponse.from(
                analysisResult,
                requirements
        );
    }

    @Transactional
    public AnalysisSaveResponse saveResume(Long userId, Long analysisResultId, String resumeCurrentText) {
        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        LocalDateTime savedAt = LocalDateTime.now();
        analysisResult.getUserResume().updateResumeContent(resumeCurrentText, savedAt);
        analysisResultRepository.flush();

        return AnalysisSaveResponse.from(analysisResult);
    }

    @Transactional
    public ReanalysisResponse reanalyze(Long userId, Long analysisResultId, String resumeCurrentText) {
        if (!hasText(resumeCurrentText)) {
            throw new CustomException(ErrorCode.EMPTY_RESUME_CONTENT);
        }

        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);
        validateRetryCount(analysisResult);

        List<JobRequirement> existingRequirements =
                jobRequirementRepository.findAllByAnalysisResultOrderByInputOrderAscIdAsc(analysisResult);
        if (existingRequirements.isEmpty()) {
            throw new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND);
        }

        String trimmedResumeText = resumeCurrentText.trim();
        GeminiAnalysisResponse reanalysisResponse = geminiAnalysisClient.reanalyze(
                buildReanalysisPrompt(existingRequirements, trimmedResumeText)
        );
        validateReanalysisResponse(reanalysisResponse, existingRequirements);

        Map<String, GeminiRequirementResult> resultByReqId = new HashMap<>();
        for (GeminiRequirementResult item : reanalysisResponse.requirements()) {
            resultByReqId.put(item.reqId(), item);
        }
        Map<String, GeminiPriorityScoreResult> priorityScoreByReqId = scoreRedYellowRequirements(
                reanalysisResponse.requirements(),
                analysisResult.getJobDescription().getJdContent(),
                trimmedResumeText
        );

        for (JobRequirement requirement : existingRequirements) {
            GeminiRequirementResult item = resultByReqId.get(reanalysisReqId(requirement));
            MatchStatus matchStatus = parseMatchStatus(item.matchStatus());
            GeminiPriorityScoreResult priorityScore = priorityScoreByReqId.get(item.reqId());
            RequirementEvaluation evaluation = requirementEvaluationRepository.findByJobRequirement(requirement)
                    .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));

            evaluation.updateReanalysis(
                    matchStatus,
                    defaultIfBlank(item.resumeEvidence(), "없음"),
                    item.judgeReason(),
                    priorityScore != null ? normalizeScore(priorityScore.effect_score()) : null,
                    priorityScore != null ? normalizeScore(priorityScore.effort_score()) : null,
                    calculatePriorityScore(priorityScore)
            );
        }

        CountResult countResult = countStatus(reanalysisResponse.requirements());
        LocalDateTime reanalyzedAt = LocalDateTime.now();
        analysisResult.getUserResume().updateResumeContent(trimmedResumeText, reanalyzedAt);
        analysisResult.applyReanalysis(
                calculateOverallLevel(existingRequirements, resultByReqId),
                countResult.redCount(),
                countResult.yellowCount(),
                countResult.greenCount(),
                reanalyzedAt
        );

        AnalysisAttempt previousAttempt = analysisAttemptRepository.findTopByAnalysisResultOrderByAttemptNoDesc(analysisResult)
                .orElseGet(() -> analysisAttemptRepository.save(AnalysisAttempt.initial(analysisResult)));
        analysisAttemptRepository.save(AnalysisAttempt.reanalysis(analysisResult, previousAttempt, "재분석"));
        analysisResultRepository.flush();

        return ReanalysisResponse.from(
                analysisResult,
                getRequirementResponses(analysisResult)
        );
    }

    @Transactional
    public AnalysisFinalSaveResponse finalSaveAnalysis(
            Long userId,
            Long analysisResultId,
            String resumeCurrentText
    ) {
        if (!hasText(resumeCurrentText)) {
            throw new CustomException(ErrorCode.EMPTY_RESUME_CONTENT);
        }

        AnalysisResult analysisResult = findOwnedAnalysisResult(userId, analysisResultId);

        LocalDateTime savedAt = LocalDateTime.now();
        analysisResult.getUserResume().updateResumeContent(resumeCurrentText.trim(), savedAt);
        analysisResult.markSaved(savedAt);
        analysisResultRepository.flush();

        return AnalysisFinalSaveResponse.from(analysisResult);
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

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CustomException(ErrorCode.INVALID_PAGE_REQUEST);
        }
    }

    private void validateRetryCount(AnalysisResult analysisResult) {
        if (analysisResult.getRetryCount() >= MAX_RETRY_COUNT) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<JobRequirementResponse> saveRequirementsAndEvaluations(
            AnalysisResult analysisResult,
            List<GeminiRequirementResult> requirementResults,
            Map<String, GeminiPriorityScoreResult> priorityScoreByReqId,
            Map<String, GeminiCardContentResult> cardContentByReqId
    ) {
        List<JobRequirementResponse> responses = new ArrayList<>();

        int inputOrder = 0;
        for (GeminiRequirementResult item : requirementResults) {
            MatchStatus matchStatus = parseMatchStatus(item.matchStatus());
            RequirementCategory category = parseRequirementCategory(item.category());
            GeminiPriorityScoreResult priorityScore = priorityScoreByReqId.get(item.reqId());
            GeminiCardContentResult cardContent = cardContentByReqId.get(item.reqId());
            JobRequirement requirement = jobRequirementRepository.save(
                    JobRequirement.builder()
                            .analysisResult(analysisResult)
                            .requirementType(parseRequirementType(category))
                            .category(category)
                            .title(defaultIfBlank(item.title(), "요건"))
                            .description(defaultIfBlank(item.description(), item.title()))
                            .jdEvidence(item.sourceText())
                            .inputOrder(inputOrder++)
                            .build()
            );

            RequirementEvaluation evaluation = requirementEvaluationRepository.save(
                    RequirementEvaluation.builder()
                            .jobRequirement(requirement)
                            .matchStatus(matchStatus)
                            .displayTitle(defaultIfBlank(cardContent != null ? cardContent.title() : null, item.title()))
                            .resumeEvidence(normalizeEvaluationText(matchStatus, item.resumeEvidence()))
                            .judgeReason(item.judgeReason())
                            .feedback(defaultIfBlank(cardContent != null ? cardContent.feedback() : null, item.feedback()))
                            .revisionSuggestion(normalizeEvaluationText(matchStatus, item.revisionSuggestion()))
                            .effectScore(priorityScore != null ? normalizeScore(priorityScore.effect_score()) : null)
                            .effortScore(priorityScore != null ? normalizeScore(priorityScore.effort_score()) : null)
                            .priorityScore(calculatePriorityScore(priorityScore))
                            .sortOrder(inputOrder - 1)
                            .build()
            );

            responses.add(JobRequirementResponse.from(requirement, evaluation));
        }

        return sortRequirementResponses(responses);
    }

    private Map<String, GeminiPriorityScoreResult> scoreRedYellowRequirements(
            List<GeminiRequirementResult> requirementResults,
            String jobPostingRawText,
            String resumeText
    ) {
        List<GeminiRequirementResult> scoringTargets = requirementResults.stream()
                .filter(requirement -> {
                    MatchStatus status = parseMatchStatus(requirement.matchStatus());
                    return status == MatchStatus.red || status == MatchStatus.yellow;
                })
                .toList();

        if (scoringTargets.isEmpty()) {
            return Map.of();
        }

        List<GeminiPriorityScoreResult> scoreResults = geminiAnalysisClient.scorePriorities(
                buildPriorityScorePrompt(scoringTargets, jobPostingRawText, resumeText)
        );
        Map<String, GeminiPriorityScoreResult> scoreByReqId = new HashMap<>();

        for (GeminiPriorityScoreResult scoreResult : scoreResults) {
            if (hasText(scoreResult.req_id())) {
                scoreByReqId.put(scoreResult.req_id(), scoreResult);
            }
        }

        return scoreByReqId;
    }

    private Map<String, GeminiCardContentResult> createCardContents(
            List<GeminiRequirementResult> requirementResults,
            Map<String, GeminiPriorityScoreResult> priorityScoreByReqId,
            String jobPostingRawText,
            String resumeText
    ) {
        List<GeminiCardContentResult> cardContents = geminiAnalysisClient.createCardContents(
                buildCardContentPrompt(requirementResults, priorityScoreByReqId, jobPostingRawText, resumeText)
        );
        Map<String, GeminiCardContentResult> cardContentByReqId = new HashMap<>();

        for (GeminiCardContentResult cardContent : cardContents) {
            if (hasText(cardContent.req_id())) {
                cardContentByReqId.put(cardContent.req_id(), cardContent);
            }
        }

        return cardContentByReqId;
    }

    private List<JobRequirementResponse> getRequirementResponses(AnalysisResult analysisResult) {
        List<JobRequirementResponse> responses = jobRequirementRepository.findAllByAnalysisResultOrderByInputOrderAscIdAsc(analysisResult).stream()
                .map(requirement -> JobRequirementResponse.from(
                        requirement,
                        requirementEvaluationRepository.findByJobRequirement(requirement)
                                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND))
                ))
                .toList();

        return sortRequirementResponses(responses);
    }

    private List<JobRequirementResponse> sortRequirementResponses(List<JobRequirementResponse> responses) {
        return responses.stream()
                .sorted(Comparator
                        .comparingInt(this::sectionOrder)
                        .thenComparingInt(this::statusSortGroup)
                        .thenComparing(this::prioritySortValue, Comparator.reverseOrder())
                        .thenComparing(JobRequirementResponse::getInputOrder)
                )
                .toList();
    }

    private int sectionOrder(JobRequirementResponse response) {
        return "PREFERRED".equals(response.getRequirementType()) ? 1 : 0;
    }

    private int statusSortGroup(JobRequirementResponse response) {
        return response.getEvaluation().getMatchStatus() == MatchStatus.green ? 1 : 0;
    }

    private BigDecimal prioritySortValue(JobRequirementResponse response) {
        BigDecimal priorityScore = response.getEvaluation().getPriorityScore();
        return priorityScore != null ? priorityScore : BigDecimal.valueOf(-1);
    }

    private void validatePdf(MultipartFile resumePdf) {
        if (resumePdf == null) {
            log.warn("Invalid PDF upload: file part is missing");
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }

        if (resumePdf.isEmpty()) {
            log.warn(
                    "Invalid PDF upload: file part is empty, partName={}, contentType={}, size={}",
                    resumePdf.getName(),
                    resumePdf.getContentType(),
                    resumePdf.getSize()
            );
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }

        if (resumePdf.getSize() > MAX_RESUME_PDF_SIZE) {
            log.warn(
                    "Invalid PDF upload: file is too large, partName={}, contentType={}, size={}",
                    resumePdf.getName(),
                    resumePdf.getContentType(),
                    resumePdf.getSize()
            );
            throw new CustomException(ErrorCode.PDF_FILE_TOO_LARGE);
        }

        int pdfHeaderOffset = findPdfHeaderOffset(resumePdf);
        if (pdfHeaderOffset < 0) {
            log.warn(
                    "Invalid PDF upload: PDF header not found, partName={}, contentType={}, size={}",
                    resumePdf.getName(),
                    resumePdf.getContentType(),
                    resumePdf.getSize()
            );
            throw new CustomException(ErrorCode.INVALID_PDF_FILE);
        }
    }

    private String extractResumeText(MultipartFile resumePdf) {
        try (PDDocument document = Loader.loadPDF(resumePdf.getBytes())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);

            String text = normalizeExtractedPdfText(textStripper.getText(document));
            if (!hasText(text)) {
                throw new CustomException(ErrorCode.UNREADABLE_PDF_TEXT);
            }

            return text;
        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Failed to extract text from resume PDF, partName={}", resumePdf.getName(), e);
            throw new CustomException(ErrorCode.UNREADABLE_PDF_TEXT);
        }
    }

    private String normalizeExtractedPdfText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private int findPdfHeaderOffset(MultipartFile resumePdf) {
        try (InputStream inputStream = resumePdf.getInputStream()) {
            byte[] bytes = inputStream.readNBytes(PDF_HEADER_SCAN_LIMIT);

            for (int offset = 0; offset <= bytes.length - PDF_HEADER.length; offset++) {
                if (hasPdfHeaderAt(bytes, offset)) {
                    return offset;
                }
            }

            return -1;
        } catch (IOException e) {
            log.warn("Invalid PDF upload: failed to read file part, partName={}", resumePdf.getName(), e);
            return -1;
        }
    }

    private boolean hasPdfHeaderAt(byte[] bytes, int offset) {
        for (int i = 0; i < PDF_HEADER.length; i++) {
            if (bytes[offset + i] != PDF_HEADER[i]) {
                return false;
            }
        }

        return true;
    }

    private User findOrCreateTestUser(Long userId) {
        return userRepository.findById(userId)
                .orElseGet(() -> userRepository.save(User.createSocialUser(
                        "local-test@example.com",
                        Provider.GOOGLE,
                        "local-test-user",
                        "로컬 테스트 유저"
                )));
    }

    private void validateJobPostingInput(
            JobInputType jobInputType,
            String jobUrl,
            String jobText,
            List<MultipartFile> jobImages
    ) {
        if (jobInputType == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        boolean hasUrl = hasText(jobUrl);
        boolean hasJobText = hasText(jobText);
        boolean hasJobImage = firstPresentImage(jobImages) != null;

        if (jobInputType == JobInputType.URL) {
            if (!hasUrl || hasJobText || hasJobImage || !isHttpUrl(jobUrl)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }

            return;
        }

        if (jobInputType == JobInputType.TEXT) {
            if (hasUrl || !hasJobText || hasJobImage || !isValidJobTextLength(jobText)) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }

            return;
        }

        if (jobInputType == JobInputType.IMAGE) {
            if (hasUrl || hasJobText || !hasJobImage) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }

            return;
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
    }

    private void validateAnalysisResponse(GeminiAnalysisResponse analysisResponse) {
        if (analysisResponse == null
                || analysisResponse.requirements() == null
                || analysisResponse.requirements().isEmpty()) {
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }

        if (!analysisResponse.isAnalyzable()) {
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }
    }

    private void validateReanalysisResponse(
            GeminiAnalysisResponse reanalysisResponse,
            List<JobRequirement> existingRequirements
    ) {
        validateAnalysisResponse(reanalysisResponse);

        if (reanalysisResponse.requirements().size() != existingRequirements.size()) {
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        }

        Map<String, GeminiRequirementResult> resultByReqId = new HashMap<>();
        for (GeminiRequirementResult item : reanalysisResponse.requirements()) {
            if (!hasText(item.reqId())) {
                throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
            }
            resultByReqId.put(item.reqId(), item);
        }

        for (JobRequirement requirement : existingRequirements) {
            GeminiRequirementResult item = resultByReqId.get(reanalysisReqId(requirement));
            if (item == null
                    || !requirement.getTitle().equals(item.title())
                    || !requirementImportance(requirement).equals(item.category())
                    || !hasText(item.matchStatus())
                    || !hasText(item.resumeEvidence())
                    || !hasText(item.judgeReason())) {
                throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
            }
        }
    }

    private void validateJobDescriptionResponse(GeminiJobDescriptionResponse jobDescriptionResponse) {
        if (jobDescriptionResponse == null
                || !jobDescriptionResponse.success()
                || !hasText(jobDescriptionResponse.jdContent())) {
            throw new CustomException(ErrorCode.JOB_POSTING_CRAWL_ERROR);
        }
    }

    private String crawlJobPostingText(JobInputType jobInputType, String jobUrl, String jobText) {
        if (jobInputType == JobInputType.TEXT) {
            return jobText.trim();
        }

        if (jobInputType == JobInputType.IMAGE) {
            return "";
        }

        return jobPostingCrawler.extractText(jobUrl.trim());
    }

    private MultipartFile firstPresentImage(List<MultipartFile> jobImages) {
        if (jobImages == null || jobImages.isEmpty()) {
            return null;
        }

        return jobImages.stream()
                .filter(image -> image != null && !image.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private boolean isHttpUrl(String url) {
        String trimmed = url.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private boolean isValidJobTextLength(String jobText) {
        int length = jobText.trim().length();
        return length >= 100 && length < 6000;
    }

    private CountResult countStatus(List<GeminiRequirementResult> requirements) {
        int red = 0;
        int yellow = 0;
        int green = 0;

        for (GeminiRequirementResult requirement : requirements) {
            MatchStatus status = parseMatchStatus(requirement.matchStatus());

            if (status == MatchStatus.red) {
                red++;
            } else if (status == MatchStatus.yellow) {
                yellow++;
            } else if (status == MatchStatus.green) {
                green++;
            }
        }

        return new CountResult(red, yellow, green);
    }

    private OverallLevel calculateOverallLevel(List<GeminiRequirementResult> requirements) {
        int requiredCount = 0;
        int preferredCount = 0;
        int requiredRedCount = 0;
        double requiredScoreSum = 0.0;
        double preferredScoreSum = 0.0;

        for (GeminiRequirementResult requirement : requirements) {
            MatchStatus status = parseMatchStatus(requirement.matchStatus());
            double score = matchScore(status);

            if (parseRequirementType(parseRequirementCategory(requirement.category())) == RequirementType.PREFERRED) {
                preferredCount++;
                preferredScoreSum += score;
            } else {
                requiredCount++;
                requiredScoreSum += score;
                if (status == MatchStatus.red) {
                    requiredRedCount++;
                }
            }
        }

        return calculateOverallLevel(
                requiredCount,
                requiredScoreSum,
                requiredRedCount,
                preferredCount,
                preferredScoreSum
        );
    }

    private OverallLevel calculateOverallLevel(
            List<JobRequirement> existingRequirements,
            Map<String, GeminiRequirementResult> resultByReqId
    ) {
        int requiredCount = 0;
        int preferredCount = 0;
        int requiredRedCount = 0;
        double requiredScoreSum = 0.0;
        double preferredScoreSum = 0.0;

        for (JobRequirement requirement : existingRequirements) {
            GeminiRequirementResult result = resultByReqId.get(reanalysisReqId(requirement));
            MatchStatus status = parseMatchStatus(result.matchStatus());
            double score = matchScore(status);

            if (requirement.getRequirementType() == RequirementType.PREFERRED) {
                preferredCount++;
                preferredScoreSum += score;
            } else {
                requiredCount++;
                requiredScoreSum += score;
                if (status == MatchStatus.red) {
                    requiredRedCount++;
                }
            }
        }

        return calculateOverallLevel(
                requiredCount,
                requiredScoreSum,
                requiredRedCount,
                preferredCount,
                preferredScoreSum
        );
    }

    private OverallLevel calculateOverallLevel(
            int requiredCount,
            double requiredScoreSum,
            int requiredRedCount,
            int preferredCount,
            double preferredScoreSum
    ) {
        double requiredRate = requiredCount > 0 ? requiredScoreSum / requiredCount : 1.0;
        double preferredRate = preferredCount > 0 ? preferredScoreSum / preferredCount : 1.0;
        double fitScore = (requiredRate * 0.8 + preferredRate * 0.2) * 100;

        if (requiredRedCount >= 2) {
            return OverallLevel.LOW;
        }

        if (requiredRedCount == 1) {
            return fitScore >= 50 ? OverallLevel.MEDIUM : OverallLevel.LOW;
        }

        if (fitScore >= 75) {
            return OverallLevel.HIGH;
        }

        if (fitScore >= 50) {
            return OverallLevel.MEDIUM;
        }

        return OverallLevel.LOW;
    }

    private double matchScore(MatchStatus status) {
        return switch (status) {
            case green -> 1.0;
            case yellow -> 0.7;
            case red -> 0.0;
        };
    }

    private RequirementCategory parseRequirementCategory(String category) {
        if ("preferred".equalsIgnoreCase(category)
                || "PREFERRED".equalsIgnoreCase(category)
                || "우대사항".equals(category)
                || "우대".equals(category)) {
            return RequirementCategory.PREFERENCE;
        }

        return RequirementCategory.QUALIFICATION;
    }

    private RequirementType parseRequirementType(RequirementCategory category) {
        return category == RequirementCategory.PREFERENCE
                ? RequirementType.PREFERRED
                : RequirementType.REQUIRED;
    }

    private String reanalysisReqId(JobRequirement requirement) {
        return "r" + (requirement.getInputOrder() + 1);
    }

    private String requirementImportance(JobRequirement requirement) {
        return requirement.getRequirementType() == RequirementType.PREFERRED
                || requirement.getCategory() == RequirementCategory.PREFERENCE
                ? "우대"
                : "필수";
    }

    private MatchStatus parseMatchStatus(String status) {
        if ("green".equalsIgnoreCase(status)
                || "met".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status)
                || "확인됨".equals(status)) {
            return MatchStatus.green;
        }

        if ("red".equalsIgnoreCase(status)
                || "gap".equalsIgnoreCase(status)
                || "MISSING".equalsIgnoreCase(status)
                || "없음".equals(status)) {
            return MatchStatus.red;
        }

        return MatchStatus.yellow;
    }

    private Integer normalizeScore(Integer score) {
        if (score == null) {
            return null;
        }

        return Math.max(1, Math.min(5, score));
    }

    private BigDecimal calculatePriorityScore(GeminiPriorityScoreResult priorityScore) {
        if (priorityScore == null) {
            return null;
        }

        int effectScore = normalizeScore(priorityScore.effect_score());
        int effortScore = normalizeScore(priorityScore.effort_score());

        return BigDecimal.valueOf((long) effectScore * effectScore)
                .divide(BigDecimal.valueOf(effortScore), 2, RoundingMode.HALF_UP);
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
        if (matchStatus == MatchStatus.green) {
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
                # 역할
                너는 채용 공고 입력 처리기다. 입력이 URL, 붙여넣은 텍스트,
                이미지 중 무엇이든 공고의 원문 텍스트를 그대로 확보한다.

                # 입력 종류별 처리
                - URL: 페이지를 읽어 공고 본문 텍스트를 가져온다.
                - 텍스트: 그대로 사용한다.
                - 이미지: 이미지 속 공고 내용을 읽어(OCR) 텍스트로 옮긴다.

                # 규칙
                - 공고 본문을 있는 그대로 확보한다. 이 단계에서는 요약·분류·삭제하지 마라.
                  (요건 추출은 다음 단계가 한다)
                - 원문의 줄·항목 구조를 최대한 보존한다.
                - 이미지의 경우 글자를 임의로 지어내지 마라. 안 보이면 안 보인다고 하라.

                # 불러오기 실패 판정
                아래 경우 success: false (사유는 구분하지 않는다):
                - URL 접근 불가 / 페이지 로드 실패
                - 크롤링·입력 결과가 비어 있거나 텍스트가 거의 없음
                - 이미지가 흐릿해 글자를 읽을 수 없음
                - 입력이 채용 공고가 아님
                - 개발자 채용 공고가 아님

                # 출력
                JSON 객체 하나만 반환해. 코드블록과 JSON 밖 설명은 쓰지 마.
                성공 시:
                { "success": true, "raw_text": "공고 원문 전체 텍스트" }
                실패 시:
                { "success": false, "raw_text": "" }

                현재 시점: %s
                채용 플랫폼 추정값: %s
                입력 URL:
                %s

                입력 텍스트:
                %s
                """.formatted(nowText(), platform, defaultIfBlank(jobPostingUrl, "없음"), jobPostingText);
    }

    private String buildAnalysisPrompt(String resumeContent, String jdContent) {
        return """
                # 역할
                너는 이력서와 채용공고를 대조해 각 요건의 충족도를 판정하는 엔진이다.
                점수(숫자)를 매기지 마라. 오직 green / yellow / red 중 하나로만 판정한다.

                # 입력
                - 공고 원문 텍스트 (LLM1의 raw_text)
                - 이력서 원문 텍스트

                # 【최우선 규칙】 누락·요약·생략 절대 금지
                자격요건·우대사항·주요업무는 분석의 핵심 근거다.
                - 원문에 있는 요건을 하나라도 빠뜨리지 마라. 전부 추출하라.
                - 여러 항목을 하나로 합치거나 요약하지 마라. 원문 항목 수를 유지하라.
                - 원문 표현을 마음대로 바꾸거나 줄이지 마라. 의미 손실 없이 담아라.
                - 주요업무에서 도출한 요건은 우대로 분류한다. 자격요건에 명시되지 않은 항목은 필수로 보지 않는다.
                - 필수인지 우대인지 애매하면 버리지 말고 반드시 둘 중 하나에 넣어라.
                  버리는 것보다 포함이 항상 우선이다.

                ## STEP 1: 요건 추출
                1) 필수/우대 구분: 공고의 "필수·자격요건"에 있으면 필수,
                   "우대·플러스·preferred"에 있으면 우대. 원문 위치로 구분하라.
                2) 묶인 기술은 쪼갠다: "Java/Spring 기반 REST API 개발"
                   -> "Java/Spring 경험" + "REST API 개발 경험"
                3) 검증 불가능한 태도·인성 요건은 제외한다.
                4) 주요업무에서 도출되는 역량도 요건으로 만든다. 이 경우 importance는 "우대"로 둔다.

                ## STEP 2: 충족도 판정
                - 구체적으로 있음 (역할·기술·성과 중 2개 이상 명시): green
                - 언급은 있으나 얕음 (기술명만, 활용 맥락 없음): yellow
                - 아예 없음: red

                # 판정 규칙
                - 공고가 카테고리로 요구하면 동등 기술을 인정한다.
                - 특정 기술을 콕 집으면 정확히 일치해야 하며, 유사기술만 있으면 yellow.
                - 프로젝트 설명 속 기술도 찾아낸다.
                - 같은 것의 다른 표현을 놓치지 마라. REST API=RESTful, Git=형상관리 등.
                - 없는 경험을 지어내지 마라. 없으면 red.
                - 애매하게 판정하지 마라. 확실하게 하나로.

                # 근거 강제
                모든 requirements 항목은 jd_evidence, resume_evidence, judge_reason을 반드시 포함한다.
                resume_evidence가 없으면 "없음"으로 쓴다.
                judge_reason은 카드의 근거 칸에 그대로 노출될 문장으로 작성한다.

                # 분석 불가 판정
                분석 불가 시 analyzable: false와 fail_side를 반환한다.
                - 공고 문제: "posting"
                - 이력서 문제: "resume"

                # 출력
                JSON 객체 하나만 반환해. 코드블록과 JSON 밖 설명은 쓰지 마.
                {
                  "analyzable": true,
                  "fail_side": null,
                  "position": "포지션명",
                  "requirements": [
                    {
                      "req_id": "r1",
                      "content": "요건 원문",
                      "importance": "필수 | 우대",
                      "status": "green | yellow | red",
                      "jd_evidence": "공고 원문 발췌",
                      "resume_evidence": "이력서 원문 발췌 또는 없음",
                      "judge_reason": "판정 이유"
                    }
                  ]
                }

                [채용공고]
                %s

                [지원자 이력서]
                %s
                """.formatted(jdContent, resumeContent);
    }

    private String buildReanalysisPrompt(List<JobRequirement> existingRequirements, String resumeCurrentText) {
        return """
                # 역할
                너는 이력서와 채용공고를 대조해 각 요건의 충족도를 판정하는 엔진이다.
                이번 요청은 재분석이다. 유저가 이력서를 수정한 뒤 다시 분석하는 상황이다.

                # 입력
                - requirements: 기존 분석에서 확정된 요건 목록
                  (각 항목: req_id, content, importance[필수/우대])
                - 수정된 이력서 원문 텍스트

                # 【최우선 규칙】 요건은 절대 건드리지 마라
                요건은 이미 확정되어 있다. 다시 뽑지 마라.
                - 요건을 추가하지 마라.
                - 요건을 삭제하지 마라.
                - content(요건 문구)를 바꾸거나 다듬지 마라.
                - importance(필수/우대)를 바꾸지 마라.
                - req_id를 그대로 유지하라. 이전 분석과 항목을 짝짓는 기준이다.

                입력으로 받은 요건 개수와 출력 개수가 반드시 같아야 한다.

                # 네가 할 일
                각 요건을 '수정된 이력서'와 대조해 status와 judge_reason만 새로 판정한다.

                ## 충족도 판정 기준 (필수·우대 동일)
                - green = 구체적으로 있음 (역할·기술·성과 중 2개 이상 명시)
                - yellow = 언급은 있으나 얕음 (기술명만, 활용 맥락 없음)
                - red = 아예 없음

                "구체적"의 정의 — 아래 중 2개 이상 명시 시 green:
                역할(설계/구현/운영/최적화), 기술(도구), 성과(정량 결과·산출물)

                ## 판정 규칙
                1) 동등 기술: 공고가 카테고리로 요구하면 동등 기술 인정(green).
                   특정 기술을 콕 집었는데 유사 기술만 있으면 yellow.
                2) 프로젝트 설명 속 기술도 찾아낸다. 기술스택 목록에 없어도
                   프로젝트에서 사용이 확인되면 인정. 이름만 있고 활용 근거 없으면 yellow.
                3) 같은 것의 다른 표현을 놓치지 마라 (REST API=RESTful, Git=형상관리 등).
                4) 없는 경험을 지어내지 마라. 이력서에 실제 있는 것만 근거로.
                5) 애매하게 판정하지 마라("~일 수 있음" 금지). 확실하게 하나로.
                6) 이전 판정에 얽매이지 마라. 지금 이력서만 보고 새로 판정한다.
                   수정으로 좋아졌으면 올리고, 그대로면 그대로 둔다.

                # 근거 강제 (모든 판정에 필수)
                - jd_evidence: 이 요건의 공고 근거 (기존 content 기준)
                - resume_evidence: 수정된 이력서 어디서 근거를 찾았는가
                  (원문 발췌, 없으면 "없음")
                - judge_reason: 왜 이 충족도인지 유저가 읽는 문장으로 작성.
                  카드의 '근거' 칸에 그대로 노출된다.
                  판정과 뉘앙스가 어긋나지 않게 쓴다(red인데 완곡하게 흐리지 마라).

                # 출력
                JSON 객체 하나만 반환해. 코드블록과 JSON 밖 설명은 쓰지 마.
                입력 요건과 개수·req_id는 반드시 동일해야 한다.
                {
                  "requirements": [
                    {
                      "req_id": "r1",
                      "content": "React 기반 개발 경험",
                      "importance": "필수",
                      "status": "green",
                      "jd_evidence": "자격요건: React 기반 개발 경험",
                      "resume_evidence": "React 기반 대시보드 설계 및 구현, 렌더링 30% 개선",
                      "judge_reason": "이번 수정에서 React 프로젝트 경험이 역할과 성과까지 함께 추가돼 충족돼요."
                    }
                  ]
                }

                [requirements]
                %s

                [수정된 이력서 원문]
                %s
                """.formatted(buildReanalysisRequirementText(existingRequirements), resumeCurrentText);
    }

    private String buildReanalysisRequirementText(List<JobRequirement> existingRequirements) {
        StringBuilder builder = new StringBuilder();

        for (JobRequirement requirement : existingRequirements) {
            builder.append("- req_id: ").append(reanalysisReqId(requirement)).append('\n')
                    .append("  content: ").append(requirement.getTitle()).append('\n')
                    .append("  importance: ").append(requirementImportance(requirement)).append('\n')
                    .append("  jd_evidence: ").append(defaultIfBlank(requirement.getJdEvidence(), requirement.getTitle())).append('\n');
        }

        return builder.toString();
    }

    private String buildPriorityScorePrompt(
            List<GeminiRequirementResult> scoringTargets,
            String jobPostingRawText,
            String resumeText
    ) {
        return """
                # 역할
                너는 IT 채용·이력서 전문가다. red/yellow 요건 각각에 대해
                두 가지를 독립적으로 판정한다: 영향력(effect_score), 난이도(effort_score).

                # 입력
                - 요건 목록 (req_id, content, importance, status[red/yellow])
                - 공고 원문, 이력서 원문
                ※ green 항목은 입력에 넣지 않는다. green은 고칠 게 없어 우선순위 채점이 불필요하다.

                # 두 축을 반드시 따로 판정하라
                effect_score는 '공고에서의 중요도'만 본다. 고치기 쉬운지 고려 금지.
                effort_score는 '이력서 기준 수정 난이도'만 본다. 중요한지 고려 금지.
                두 점수는 서로 영향을 주지 않는다.

                # effect_score 기준 (1~5) — 클수록 영향력 큼
                5 — 직무 핵심. 공고 전반(제목·자격요건·주요업무)에 반복 등장하거나 없으면 서류에서 즉시 탈락하는 결정적 항목.
                4 — 자격요건에 명시된 필수 기술/경험. 직무 표준 역량.
                3 — 업무에 필요하나 단독으로 당락을 가르진 않는 항목.
                2 — 있으면 유리하나 없어도 통과 가능. 우대 수준.
                1 — 부차적으로 한 번 언급. 대체 경험으로 갈음 가능.
                ※ importance를 참고하되 그대로 복사 금지. 강조 빈도·위치를 근거로.

                # effort_score 기준 (1~5) — 클수록 고치기 어려움
                1 — 표현만 수정. 이력서에 내용 있고 키워드·문장만 다듬으면 됨.
                2 — 내용 보강. 경험은 있으나 수치·맥락을 새로 채워야 함.
                3 — 재구성. 흩어진 경험을 공고 관점으로 묶거나 재서술.
                4 — 학습·단기 확보. 없으나 학습·짧은 프로젝트로 빠르게 근거 확보 가능.
                5 — 신규 확보. 관련 경험 전무, 실무·큰 프로젝트를 새로 쌓아야 함.
                ※ status를 그대로 매핑 금지. 같은 red라도 학습(4)과 실무(5)는 다르다.

                # 출력
                JSON 배열 하나만 반환해. 코드블록과 JSON 밖 설명은 쓰지 마.
                [
                  {
                    "req_id": "r1",
                    "effect_score": 5,
                    "effort_score": 5,
                    "reason": "effect: 공고 3곳에 반복된 직무 핵심 기술 / effort: 이력서에 관련 경험이 없어 새로 확보 필요"
                  }
                ]

                # reason 작성 규칙
                - effect_score 근거와 effort_score 근거를 각각 밝힌다.
                - 점수 숫자와 논리적으로 일치해야 한다.
                - 내부 채점 근거용이며 유저에게 노출하지 않는다.

                [요건 목록]
                %s

                [공고 원문]
                %s

                [이력서 원문]
                %s
                """.formatted(buildPriorityScoreTargetText(scoringTargets), jobPostingRawText, resumeText);
    }

    private String buildPriorityScoreTargetText(List<GeminiRequirementResult> scoringTargets) {
        StringBuilder builder = new StringBuilder();

        for (GeminiRequirementResult target : scoringTargets) {
            builder.append("- req_id: ").append(target.reqId()).append('\n')
                    .append("  content: ").append(target.title()).append('\n')
                    .append("  importance: ").append(target.category()).append('\n')
                    .append("  status: ").append(target.matchStatus()).append('\n');
        }

        return builder.toString();
    }

    private String buildCardContentPrompt(
            List<GeminiRequirementResult> requirementResults,
            Map<String, GeminiPriorityScoreResult> priorityScoreByReqId,
            String jobPostingRawText,
            String resumeText
    ) {
        return """
                # 역할
                너는 IT 이력서 첨삭 코치다. 각 요건에 대해 카드에 표시할
                제목(title)과 피드백(feedback)을 작성한다.

                # 입력
                - 요건 목록 (req_id, content, importance, status[green/yellow/red],
                  LLM2의 jd_evidence·resume_evidence·judge_reason,
                  effect_score·effort_score는 red/yellow에만 있음)
                - 이력서 원문, 공고 원문
                ※ green·yellow·red 모두 생성한다.

                # title 작성 규칙
                - green → 공고 항목명 그대로
                - red → 공고 항목명 그대로
                - yellow → 피드백 한 줄 요약

                # feedback 작성 규칙
                - 유저가 읽고 '바로 무엇을 할지' 알 수 있게 쓴다.
                - LLM2의 판정·근거와 어긋나지 않게 쓴다.
                - 이력서에 실제 있는 내용만 근거로 하고, 없는 경험을 지어내지 마라.
                - green: 이미 잘 갖춰진 항목. 어떻게 더 강조·부각하면 좋을지 보강 제안.
                - yellow: 기존 문장을 어떻게 고칠지. 필요하면 피드백 문장 안에 수정 예시 포함.
                - red: 무엇을 추가하거나 어떤 경험을 쌓을지.
                - red/yellow에서 effort_score가 낮으면 당장 할 표현 수정 위주, 높으면 현실적 확보 방향.
                - 한 항목당 2~3문장. 권유조로 "~하면 좋아요"처럼 쓴다.

                # 중요
                - 카드의 근거 칸에는 LLM2의 judge_reason이 그대로 들어간다.
                - judge_reason을 새로 만들거나 출력하지 마라.

                # 출력
                JSON 배열 하나만 반환해. 코드블록과 JSON 밖 설명은 쓰지 마.
                [
                  {
                    "req_id": "r1",
                    "status": "green",
                    "title": "REST API 설계 및 개발 경험",
                    "feedback": "이미 잘 갖춰진 항목이에요. API 개수뿐 아니라 응답속도 개선 같은 성과 수치를 함께 적으면 이 강점이 더 부각돼요."
                  }
                ]

                [요건 목록]
                %s

                [공고 원문]
                %s

                [이력서 원문]
                %s
                """.formatted(buildCardContentTargetText(requirementResults, priorityScoreByReqId), jobPostingRawText, resumeText);
    }

    private String buildCardContentTargetText(
            List<GeminiRequirementResult> requirementResults,
            Map<String, GeminiPriorityScoreResult> priorityScoreByReqId
    ) {
        StringBuilder builder = new StringBuilder();

        for (GeminiRequirementResult requirement : requirementResults) {
            GeminiPriorityScoreResult priorityScore = priorityScoreByReqId.get(requirement.reqId());
            builder.append("- req_id: ").append(requirement.reqId()).append('\n')
                    .append("  content: ").append(requirement.title()).append('\n')
                    .append("  importance: ").append(requirement.category()).append('\n')
                    .append("  status: ").append(requirement.matchStatus()).append('\n')
                    .append("  jd_evidence: ").append(requirement.sourceText()).append('\n')
                    .append("  resume_evidence: ").append(requirement.resumeEvidence()).append('\n')
                    .append("  judge_reason: ").append(requirement.judgeReason()).append('\n');

            if (priorityScore != null) {
                builder.append("  effect_score: ").append(priorityScore.effect_score()).append('\n')
                        .append("  effort_score: ").append(priorityScore.effort_score()).append('\n');
            }
        }

        return builder.toString();
    }

    private record CountResult(
            int redCount,
            int yellowCount,
            int greenCount
    ) {
    }
}
