package com.backend.analysis_20260710.client;

import com.backend.analysis_20260710.dto.GeminiAnalysisResponse;
import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.util.Base64;
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

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiAnalysisClient(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model
    ) {
        this.geminiRestClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public GeminiAnalysisResponse analyze(MultipartFile resumePdf, String prompt) {
        try {
            String encodedPdf = Base64.getEncoder().encodeToString(resumePdf.getBytes());

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(
                                            Map.of(
                                                    "inline_data", Map.of(
                                                            "mime_type", "application/pdf",
                                                            "data", encodedPdf
                                                    )
                                            ),
                                            Map.of("text", prompt)
                                    )
                            )
                    ),
                    "generationConfig", Map.of(
                            "response_mime_type", "application/json"
                    )
            );

            String rawResponse = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String jsonText = extractJsonText(rawResponse);
            return objectMapper.readValue(jsonText, GeminiAnalysisResponse.class);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        } catch (JacksonException e) {
            log.warn("Failed to parse Gemini analysis response: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    private String extractJsonText(String rawResponse) throws JacksonException {
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
        return text
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}
