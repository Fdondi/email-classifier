package com.emailtool.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.emailtool.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class LlmLifecycleTest {

    private MockWebServer server;
    private Path cli;
    private final AtomicBoolean chatLoaded = new AtomicBoolean(false);

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.contains("/api/v0/models")) {
                    String body = chatLoaded.get()
                            ? "{\"data\":[{\"id\":\"gemma-4-26b-a4b-it\",\"state\":\"loaded\",\"type\":\"llm\"},"
                                    + "{\"id\":\"text-embedding-nomic-embed-text-v1.5\",\"state\":\"not-loaded\",\"type\":\"embeddings\"}]}"
                            : "{\"data\":[{\"id\":\"gemma-4-26b-a4b-it\",\"state\":\"not-loaded\",\"type\":\"llm\"},"
                                    + "{\"id\":\"text-embedding-nomic-embed-text-v1.5\",\"state\":\"not-loaded\",\"type\":\"embeddings\"}]}";
                    return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
                }
                // OpenAI /v1/models lists installed models even when none are loaded.
                String installed = "{\"data\":[{\"id\":\"gemma-4-26b-a4b-it\"},{\"id\":\"text-embedding-nomic-embed-text-v1.5\"}]}";
                return new MockResponse().setBody(installed).addHeader("Content-Type", "application/json");
            }
        });
        server.start();
        cli = Files.createTempFile("lms", ".exe");
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
        Files.deleteIfExists(cli);
    }

    @Test
    void usesAlreadyLoadedChatModelWithoutShellingOut() {
        chatLoaded.set(true);
        RecordingRunner runner = new RecordingRunner(command -> new CommandRunner.Result(0, ""));
        LlmLifecycle lifecycle = lifecycle(runner);
        assertTrue(lifecycle.ensureReady());
        assertTrue(runner.seen.isEmpty());
        lifecycle.releaseIfOwned();
        assertTrue(runner.seen.isEmpty());
    }

    @Test
    void doesNotTreatInstalledButUnloadedModelsAsReady() {
        chatLoaded.set(false);
        RecordingRunner runner = new RecordingRunner(command -> {
            if (command.get(0).equals("nvidia-smi") && command.get(1).contains("memory.used")) {
                return new CommandRunner.Result(0, "20000, 24576, 4000");
            }
            if (command.get(0).equals("nvidia-smi")) {
                return new CommandRunner.Result(0, "99, chrome.exe, 18000 MiB");
            }
            if (command.contains("ps")) {
                return new CommandRunner.Result(0, "[]");
            }
            return new CommandRunner.Result(0, "");
        });
        LlmLifecycle lifecycle = lifecycle(runner);
        assertFalse(lifecycle.ensureReady());
        assertTrue(lifecycle.lastError().contains("chrome.exe"));
        assertFalse(runner.seen.stream().anyMatch(command -> command.contains("load")));
    }

    @Test
    void doesNotLoadWhenGpuIsBusy() throws Exception {
        RecordingRunner runner = new RecordingRunner(command -> {
            if (command.get(0).equals("nvidia-smi") && command.get(1).contains("memory.used")) {
                return new CommandRunner.Result(0, "20000, 24576, 4000");
            }
            if (command.get(0).equals("nvidia-smi")) {
                return new CommandRunner.Result(0, "99, chrome.exe, 18000 MiB");
            }
            if (command.contains("ps")) {
                return new CommandRunner.Result(0, "[]");
            }
            return new CommandRunner.Result(0, "");
        });
        LlmLifecycle lifecycle = lifecycle(runner);
        assertFalse(lifecycle.ensureReady());
        assertTrue(lifecycle.lastError().contains("chrome.exe"));
        assertFalse(runner.seen.stream().anyMatch(command -> command.contains("load")));
    }

    @Test
    void loadsThenUnloadsWhenGpuIsFree() throws Exception {
        RecordingRunner runner = new RecordingRunner(command -> {
            if (command.contains("load")) {
                chatLoaded.set(true);
                return new CommandRunner.Result(0, "loaded");
            }
            if (command.get(0).equals("nvidia-smi") && command.get(1).contains("memory.used")) {
                return new CommandRunner.Result(0, "400, 24576, 24000");
            }
            if (command.get(0).equals("nvidia-smi")) {
                return new CommandRunner.Result(0, "");
            }
            if (command.contains("ps")) {
                return new CommandRunner.Result(0, chatLoaded.get() ? "[{\"modelKey\":\"gemma-4-26b-a4b-it\"}]" : "[]");
            }
            return new CommandRunner.Result(0, "unloaded");
        });
        LlmLifecycle lifecycle = lifecycle(runner);
        assertTrue(lifecycle.ensureReady());
        assertTrue(runner.seen.stream().anyMatch(command -> command.contains("load")));
        lifecycle.releaseIfOwned();
        assertTrue(runner.seen.stream().anyMatch(command -> command.contains("unload")));
        assertEquals("gemma-4-26b-a4b-it", unloadKey(runner));
    }

    private static String unloadKey(RecordingRunner runner) {
        return runner.seen.stream()
                .filter(command -> command.contains("unload"))
                .map(command -> command.get(2))
                .findFirst()
                .orElseThrow();
    }

    private LlmLifecycle lifecycle(CommandRunner runner) {
        AppProperties properties = new AppProperties();
        properties.getLlm().setBaseUrl(server.url("/v1").toString().replaceAll("/$", ""));
        properties.getLlm().setCli(cli.toString());
        properties.getLlm().setModelKey("gemma-4-26b-a4b-it");
        properties.getLlm().setLoadTimeoutSeconds(5);
        properties.getLlm().setMinFreeVramMib(18000);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient client = RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
        return new LlmLifecycle(properties, runner, new ObjectMapper(), client);
    }

    private static final class RecordingRunner implements CommandRunner {
        private final List<List<String>> seen = new ArrayList<>();
        private final Function<List<String>, CommandRunner.Result> handler;

        private RecordingRunner(Function<List<String>, CommandRunner.Result> handler) {
            this.handler = handler;
        }

        @Override
        public CommandRunner.Result run(List<String> command, Duration timeout) {
            seen.add(List.copyOf(command));
            return handler.apply(command);
        }
    }
}
