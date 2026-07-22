package com.backend.analysis.client;

import com.backend.analysis.dto.GeminiAnalysisResponse;
import com.backend.analysis.dto.GeminiJobDescriptionResponse;
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
        return generate(parts, GeminiJobDescriptionResponse.class);
    }

    public GeminiAnalysisResponse analyze(String prompt) {
        // 정리된 이력서와 공고 내용을 비교 분석
        return generate(List.of(textPart(prompt)), GeminiAnalysisResponse.class, analysisResponseSchema());
    }

    private <T> T generate(List<Map<String, Object>> parts, Class<T> responseType) {
        // 응답 스키마가 필요 없는 Gemini 호출
        return generate(parts, responseType, null);
    }

    private <T> T generate(
            List<Map<String, Object>> parts,
            Class<T> responseType,
            Map<String, Object> responseSchema
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
            return objectMapper.readValue(jsonText, responseType);
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
                                                "id", Map.of("type", "STRING"),
                                                "text", Map.of("type", "STRING"),
                                                "type", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("required", "preferred")
                                                ),
                                                "status", Map.of(
                                                        "type", "STRING",
                                                        "enum", List.of("met", "partial", "gap")
                                                ),
                                                "flag", nullableStringSchema(),
                                                "evidence", nullableStringSchema(),
                                                "feedback", nullableStringSchema(),
                                                "suggestion", nullableStringSchema()
                                        ),
                                        "required", List.of(
                                                "id",
                                                "text",
                                                "type",
                                                "status",
                                                "flag",
                                                "evidence",
                                                "feedback",
                                                "suggestion"
                                        )
                                )
                        )
                ),
                "required", List.of("company", "position", "summary", "requirements")
        );
    }

    private Map<String, Object> nullableStringSchema() {
        // Gemini response_schema에서 null을 허용하는 문자열 필드 정의
        return Map.of(
                "type", "STRING",
                "nullable", true
        );
    }
}
