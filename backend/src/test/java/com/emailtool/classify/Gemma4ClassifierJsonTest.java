package com.emailtool.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Gemma4ClassifierJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ClassifierJsonParser parser = new ClassifierJsonParser(mapper, new AppProperties());

    @Test
    void parsesCapturedGemma4MessageContent() throws Exception {
        String content = Files.readString(fixture("gemma-4-classify-content.txt"));
        ClassifierJson parsed = parser.parse(content).orElseThrow();
        assertEquals(Category.ACTION_NEEDED_PAY, parsed.category());
        assertEquals("en", parsed.language());
        assertEquals(false, parsed.injectionSuspected());
        assertEquals("2025-09-15", parsed.dueDate().toString());
    }

    @Test
    void parsesContentFromCapturedGemma4ChatCompletionsEnvelope() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(fixture("gemma-4-classify-response.json")));
        String content = root.path("choices").path(0).path("message").path("content").asText();
        assertTrue(content.contains("ACTION_NEEDED_PAY"), content);
        ClassifierJson parsed = parser.parse(content).orElseThrow();
        assertEquals(Category.ACTION_NEEDED_PAY, parsed.category());
    }

    @Test
    void reasoningTraceIsNotClassifierJson() throws Exception {
        JsonNode root = mapper.readTree(Files.readString(fixture("gemma-4-classify-response.json")));
        String reasoning = root.path("choices").path(0).path("message").path("reasoning_content").asText();
        assertTrue(reasoning.contains("ACTION_NEEDED_PAY"), "fixture should include Gemma's thinking");
        assertTrue(parser.parse(reasoning).isEmpty());
    }

    private static Path fixture(String name) {
        return Path.of("..", "testdata", "llm", name);
    }
}
