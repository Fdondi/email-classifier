package com.emailtool.config;

import com.emailtool.classify.ClassifierJsonParser;
import com.emailtool.classify.LlmClassifier;
import com.emailtool.llm.LlmRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Bean
    @Qualifier("llmRestClient")
    RestClient llmRestClient(AppProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    LlmClassifier llmClassifier(
            AppProperties properties,
            ClassifierJsonParser parser,
            ObjectMapper mapper,
            @Qualifier("llmRestClient") RestClient llmRestClient,
            LlmRuntime llmRuntime) {
        return new LlmClassifier(properties, parser, mapper, llmRestClient, llmRuntime);
    }
}
