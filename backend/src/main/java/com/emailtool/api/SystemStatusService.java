package com.emailtool.api;

import com.emailtool.api.dto.SystemStatusDto;
import com.emailtool.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    private final AppProperties properties;

    public SystemStatusService(AppProperties properties) {
        this.properties = properties;
    }

    public SystemStatusDto status() {
        AppProperties.Imap imap = properties.getMail().getImap();
        String password = imap.getPassword();
        boolean imapReady = imap.getUsername() != null
                && !imap.getUsername().isBlank()
                && password != null
                && !password.isBlank()
                && !password.startsWith("replace-");
        LlmProbe probe = probeLlm();
        String configuredModel = properties.getLlm().getModel();
        return new SystemStatusDto(
                properties.getMail().getSource() == null ? "maildir" : properties.getMail().getSource(),
                imapReady,
                imap.getHost(),
                properties.getLlm().getBaseUrl(),
                configuredModel == null || configuredModel.isBlank() ? null : configuredModel,
                probe.reachable(),
                probe.models(),
                probe.error());
    }

    private LlmProbe probeLlm() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient client = RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
        try {
            ModelsResponse response = client.get().uri(modelsPath()).retrieve().body(ModelsResponse.class);
            List<String> ids = response == null || response.data() == null
                    ? List.of()
                    : response.data().stream().map(ModelId::id).filter(id -> id != null && !id.isBlank()).toList();
            return new LlmProbe(true, ids, null);
        } catch (RestClientException e) {
            log.debug("LLM status probe failed: {}", e.getMessage());
            return new LlmProbe(false, List.of(), e.getMessage());
        }
    }

    private String modelsPath() {
        String base = properties.getLlm().getBaseUrl();
        if (base != null && base.replaceAll("/+$", "").endsWith("/v1")) {
            return "/models";
        }
        return "/v1/models";
    }

    private record LlmProbe(boolean reachable, List<String> models, String error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelsResponse(List<ModelId> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelId(String id) {}
}
