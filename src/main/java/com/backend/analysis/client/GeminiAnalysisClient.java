package com.backend.analysis.client;

import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiCardContentResult;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
import com.backend.analysis.dto.GeminiPriorityScoreResult;
import com.backend.analysis.dto.GeminiResumeResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class GeminiAnalysisClient {

    // Gemini API 호출에 사용하는 HTTP 클라이언트
    private final RestClient geminiRestClient;
    // Gemini가 반환한 JSON 문자열을 DTO로 변환
    private final ObjectMapper objectMapper;
    // application.yml의 gemini.model 값
    private final String model;

    public GeminiAnalysisClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model
    ) {
        // Gemini API 키와 모델명으로 호출 클라이언트 생성
        this.geminiRestClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public GeminiResumeResponse summarizeResume(MultipartFile resumePdf, String prompt) {
        // 이력서 PDF와 프롬프트를 함께 보내 이력서 내용을 정리
        return generate(
                List.of(inlineDataPart(resumePdf, "application/pdf"), textPart(prompt)),
                GeminiResumeResponse.class
        );
    }

    public GeminiJobDescriptionResponse summarizeJobDescription(
            String prompt,
            MultipartFile jobPostingImage
    ) {
        // 채용공고 이미지가 있으면 Gemini에 같이 전달
        List<Map<String, Object>> parts = new ArrayList<>();

        if (jobPostingImage != null && !jobPostingImage.isEmpty()) {
            parts.add(inlineDataPart(jobPostingImage, resolveImageMimeType(jobPostingImage)));
        }

        parts.add(textPart(prompt));
        return generate(parts, GeminiJobDescriptionResponse.class, jobDescriptionResponseSchema());
    }

    public GeminiAnalysisResponse analyze(String prompt) {
        // 정리된 이력서와 공고 내용을 비교 분석
        return generate(List.of(textPart(prompt)), GeminiAnalysisResponse.class, analysisResponseSchema());
    }

    public GeminiAnalysisResponse reanalyze(String prompt) {
        // 기존 요건은 유지하고 수정된 이력서 기준으로 충족도만 다시 판정
        return generate(List.of(textPart(prompt)), GeminiAnalysisResponse.class, reanalysisResponseSchema());
    }

    public List<GeminiPriorityScoreResult> scorePriorities(String prompt) {
        // red/yellow 요건만 보내 effect/effort 점수를 계산
        return generate(
                List.of(textPart(prompt)),
                new TypeReference<>() {
                },
                priorityScoreResponseSchema()
        );
    }

    public List<GeminiCardContentResult> createCardContents(String prompt) {
        // 모든 요건에 대해 카드 제목과 피드백을 생성
        return generate(
                List.of(textPart(prompt)),
                new TypeReference<>() {
                },
                cardContentResponseSchema()
        );
    }

    private <T> T generate(List<Map<String, Object>> parts, Class<T> responseType) {
        // 응답 스키마가 필요 없는 Gemini 호출
        return generate(parts, responseType, null);
    }

    private <T> T generate(
            List<Map<String, Object>> parts,
            TypeReference<T> responseType,
            Map<String, Object> responseSchema
    ) {
        return generate(parts, responseSchema, jsonText -> objectMapper.readValue(jsonText, responseType));
    }

    private <T> T generate(
            List<Map<String, Object>> parts,
            Class<T> responseType,
            Map<String, Object> responseSchema
    ) {
        return generate(parts, responseSchema, jsonText -> objectMapper.readValue(jsonText, responseType));
    }

    private <T> T generate(
            List<Map<String, Object>> parts,
            Map<String, Object> responseSchema,
            JsonParser<T> jsonParser
    ) {
        try {
            // Gemini가 JSON 응답만 반환하도록 설정
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("response_mime_type", "application/json");
            generationConfig.put("max_output_tokens", 8192);

            if (responseSchema != null) {
                generationConfig.put("response_schema", responseSchema);
            }

            // Gemini generateContent 요청 본문 구성
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", parts
                            )
                    ),
                    "generationConfig", generationConfig
            );

            String rawResponse = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // Gemini 응답 본문에서 JSON 문자열만 꺼내 DTO로 변환
            String jsonText = extractJsonText(rawResponse);
            return jsonParser.parse(jsonText);
        } catch (JacksonException e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        } catch (Exception e) {
            log.warn("Gemini API request failed: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    private Map<String, Object> inlineDataPart(MultipartFile file, String mimeType) {
        try {
            // 파일을 base64로 바꿔 Gemini inline_data 형식에 맞춤
            return Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", Base64.getEncoder().encodeToString(file.getBytes())
                    )
            );
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Map<String, Object> textPart(String text) {
        // 텍스트 프롬프트를 Gemini parts 형식으로 변환
        return Map.of("text", text);
    }

    private String resolveImageMimeType(MultipartFile image) {
        // 이미지 Content-Type이 없으면 기본 PNG로 처리
        String contentType = image.getContentType();

        if (contentType == null || contentType.isBlank()) {
            return "image/png";
        }

        return contentType;
    }

    private String extractJsonText(String rawResponse) throws JacksonException {
        // Gemini 응답에서 실제 JSON 텍스트가 들어있는 위치를 추출
        JsonNode response = objectMapper.readTree(rawResponse);

        JsonNode text = response
                .required("candidates")
                .required(0)
                .required("content")
                .required("parts")
                .required(0)
                .required("text");

        return cleanJson(text.stringValue());
    }

    private String cleanJson(String text) {
        // 혹시 포함된 Markdown 코드블록 문자를 제거
        return text
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private Map<String, Object> analysisResponseSchema() {
        // 분석 프롬프트의 출력 형식과 같은 JSON 구조로 고정
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "analyzable", Map.of("type", "BOOLEAN"),
                        "fail_side", nullableStringSchema(),
                        "company", Map.of("type", "STRING"),
                        "position", Map.of("type", "STRING"),
                        "summary", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "met_count", Map.of("type", "INTEGER"),
                                        "partial_count", Map.of("type", "INTEGER"),
                                        "gap_count", Map.of("type", "INTEGER"),
                                        "red_flag_count", Map.of("type", "INTEGER"),
                                        "yellow_flag_count", Map.of("type", "INTEGER"),
                                        "top_message", Map.of("type", "STRING")
                                ),
                                "required", List.of(
                                        "met_count",
                                        "partial_count",
                                        "gap_count",
                                        "red_flag_count",
                                        "yellow_flag_count",
                                        "top_message"
                                )
                        ),
                        "requirements", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                "req_id", Map.of("type", "STRING"),
                                                "content", Map.of("type", "STRING"),
                                                "importance", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("필수", "우대")
                                                ),
                                                "status", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("green", "yellow", "red")
                                                ),
                                                "jd_evidence", Map.of("type", "STRING"),
                                                "resume_evidence", Map.of("type", "STRING"),
                                                "judge_reason", Map.of("type", "STRING")
                                        ),
                                        "required", List.of(
                                                "req_id",
                                                "content",
                                                "importance",
                                                "status",
                                                "jd_evidence",
                                                "resume_evidence",
                                                "judge_reason"
                                        )
                                )
                        )
                ),
                "required", List.of("analyzable", "fail_side", "position", "requirements")
        );
    }

    private Map<String, Object> jobDescriptionResponseSchema() {
        // 채용공고 원문 확보 단계는 성공 여부와 원문 텍스트만 반환
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "success", Map.of("type", "BOOLEAN"),
                        "raw_text", Map.of("type", "STRING")
                ),
                "required", List.of("success", "raw_text")
        );
    }

    private Map<String, Object> reanalysisResponseSchema() {
        // 재분석은 기존 요건 목록의 판정 결과만 반환
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "requirements", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                "req_id", Map.of("type", "STRING"),
                                                "content", Map.of("type", "STRING"),
                                                "importance", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("필수", "우대")
                                                ),
                                                "status", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("green", "yellow", "red")
                                                ),
                                                "jd_evidence", Map.of("type", "STRING"),
                                                "resume_evidence", Map.of("type", "STRING"),
                                                "judge_reason", Map.of("type", "STRING")
                                        ),
                                        "required", List.of(
                                                "req_id",
                                                "content",
                                                "importance",
                                                "status",
                                                "jd_evidence",
                                                "resume_evidence",
                                                "judge_reason"
                                        )
                                )
                        )
                ),
                "required", List.of("requirements")
        );
    }

    private Map<String, Object> priorityScoreResponseSchema() {
        // LLM3 우선순위 채점은 red/yellow 요건 배열만 반환
        return Map.of(
                "type", "ARRAY",
                "items", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "req_id", Map.of("type", "STRING"),
                                "effect_score", Map.of(
                                        "type", "INTEGER",
                                        "minimum", 1,
                                        "maximum", 5
                                ),
                                "effort_score", Map.of(
                                        "type", "INTEGER",
                                        "minimum", 1,
                                        "maximum", 5
                                ),
                                "reason", Map.of("type", "STRING")
                        ),
                        "required", List.of("req_id", "effect_score", "effort_score", "reason")
                )
        );
    }

    private Map<String, Object> cardContentResponseSchema() {
        // LLM4 카드 문구 생성은 모든 요건의 title/feedback 배열을 반환
        return Map.of(
                "type", "ARRAY",
                "items", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "req_id", Map.of("type", "STRING"),
                                "status", Map.of(
                                        "type", "STRING",
                                        "enum", List.of("green", "yellow", "red")
                                ),
                                "title", Map.of("type", "STRING"),
                                "feedback", Map.of("type", "STRING")
                        ),
                        "required", List.of("req_id", "status", "title", "feedback")
                )
        );
    }

    private Map<String, Object> nullableStringSchema() {
        // Gemini response_schema에서 null을 허용하는 문자열 필드 정의
        return Map.of(
                "type", "STRING",
                "nullable", true
        );
    }

    @FunctionalInterface
    private interface JsonParser<T> {
        T parse(String jsonText) throws JacksonException;
    }
}
