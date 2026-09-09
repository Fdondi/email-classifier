package com.emailtool.classify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.config.AppProperties;
import com.emailtool.domain.Category;
import com.emailtool.domain.Email;
import com.emailtool.llm.AlreadyReadyLlmRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class LlmClassifierTest {

    private MockWebServer server;
    private LlmClassifier classifier;

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        AppProperties properties = new AppProperties();
        properties.getLlm().setBaseUrl(server.url("/v1").toString().replaceAll("/$", ""));
        properties.getLlm().setModel("test-model");
        properties.getLlm().setTimeoutSeconds(5);
        ClassifierJsonParser parser =
                new ClassifierJsonParser(new ObjectMapper().registerModule(new JavaTimeModule()), properties);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
        classifier = new LlmClassifier(properties, parser, new ObjectMapper(), restClient, new AlreadyReadyLlmRuntime());
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void retriesThenReturnsEmptyOnInvalidJson() {
        server.enqueue(openaiBody("not-json"));
        server.enqueue(openaiBody("still-bad"));
        Email email = new Email();
        email.setTextPlain("hello");
        assertTrue(classifier.classify(email).isEmpty());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void usesReasoningContentWhenMessageContentIsEmpty() {
        String inner =
                "{\"category\":\"OTHER\",\"confidence\":0.4,\"summary\":\"Note\",\"interest_score\":10,\"injection_suspected\":false,\"language\":\"en\"}";
        String body = "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":" + quote(inner) + "}}]}";
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
        Email email = new Email();
        email.setTextPlain("hello");
        ClassifierJson json = classifier.classify(email).orElseThrow();
        assertEquals(Category.OTHER, json.category());
    }

    @Test
    void parsesValidClassification() {
        String inner =
                "{\"category\":\"OTHER\",\"confidence\":0.4,\"summary\":\"Note\",\"interest_score\":10,\"injection_suspected\":true,\"language\":\"en\"}";
        server.enqueue(openaiBody(inner));
        Email email = new Email();
        email.setTextPlain("hello");
        ClassifierJson json = classifier.classify(email).orElseThrow();
        assertEquals(Category.OTHER, json.category());
        assertEquals(true, json.injectionSuspected());
        assertEquals(false, json.actionNeeded());
    }

    private static MockResponse openaiBody(String content) {
        String body = "{\"choices\":[{\"message\":{\"content\":" + quote(content) + "}}]}";
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
