package com.backend.analysis.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAnalysisClientTest {

    private final GeminiAnalysisClient geminiAnalysisClient = new GeminiAnalysisClient(
            new ObjectMapper(),
            "test-api-key",
            "test-model"
    );

    @Test
    @DisplayName("카드 문구 응답 스키마는 한끗 피드백 필드를 요구한다")
    void cardContentResponseSchemaRequiresRevisionSuggestion() {
        Map<String, Object> schema = ReflectionTestUtils.invokeMethod(
                geminiAnalysisClient,
                "cardContentResponseSchema"
        );

        Map<String, Object> items = asMap(schema.get("items"));
        Map<String, Object> properties = asMap(items.get("properties"));
        List<String> required = asList(items.get("required"));

        assertThat(properties).containsKey("revision_suggestion");
        assertThat(asMap(properties.get("revision_suggestion"))).containsEntry("nullable", true);
        assertThat(required).contains("revision_suggestion");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> asList(Object value) {
        return (List<String>) value;
    }
}
