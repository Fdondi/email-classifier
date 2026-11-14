package com.emailtool.llm;

import com.emailtool.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class LlmLifecycle implements LlmRuntime {

    private static final Logger log = LoggerFactory.getLogger(LlmLifecycle.class);

    private final AppProperties properties;
    private final CommandRunner commands;
    private final ObjectMapper mapper;
    private final RestClient modelsClient;
    private final LongSupplier freeRamMib;

    private enum Phase {
        UNKNOWN,
        READY,
        FAILED
    }

    private final Object lock = new Object();
    private Phase phase = Phase.UNKNOWN;
    private String lastError;
    private boolean loadedByUs;
    private boolean loadedOnGpu;
    private String loadedKey;

    @Autowired
    public LlmLifecycle(AppProperties properties, CommandRunner commands, ObjectMapper mapper) {
        this.properties = properties;
        this.commands = commands;
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.modelsClient = RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .build();
        // Windows: available physical memory (free + standby), same as Task Manager's "Available".
        this.freeRamMib = () -> ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getFreeMemorySize() / (1024 * 1024);
    }

    LlmLifecycle(
            AppProperties properties,
            CommandRunner commands,
            ObjectMapper mapper,
            RestClient modelsClient,
            LongSupplier freeRamMib) {
        this.properties = properties;
        this.commands = commands;
        this.mapper = mapper;
        this.modelsClient = modelsClient;
        this.freeRamMib = freeRamMib;
    }

    @Override
    public boolean ensureReady() {
        synchronized (lock) {
            switch (phase) {
                case READY:
                    return true;
                case FAILED:
                    return false;
                case UNKNOWN:
                    boolean ok = prepare();
                    phase = ok ? Phase.READY : Phase.FAILED;
                    return ok;
                default: {
                    Phase exhaustive = phase;
                    throw new IllegalStateException("Unhandled phase: " + exhaustive);
                }
            }
        }
    }

    @Override
    public String lastError() {
        synchronized (lock) {
            return lastError;
        }
    }

    @Override
    public void releaseIfOwned() {
        synchronized (lock) {
            if (loadedByUs && loadedKey != null && properties.getLlm().isAutoUnload()) {
                Path cli = resolveCli();
                if (cli != null) {
                    long started = System.currentTimeMillis();
                    CommandRunner.Result unloaded =
                            commands.run(List.of(cli.toString(), "unload", loadedKey), Duration.ofMinutes(5));
                    log.info(
                            "lms unload {} (exit {}, {} ms)",
                            loadedKey,
                            unloaded.exitCode(),
                            System.currentTimeMillis() - started);
                    // A CPU load never held the GPU; its other user would make this poll time out.
                    String remaining = loadedOnGpu ? waitForGpuReleased() : null;
                    if (remaining != null) {
                        log.warn("GPU still occupied after unload: {}", remaining);
                    }
                }
            }
            loadedByUs = false;
            loadedOnGpu = false;
            loadedKey = null;
            phase = Phase.UNKNOWN;
            lastError = null;
        }
    }

    private boolean prepare() {
        lastError = null;
        if (!loadedChatIds().isEmpty()) {
            return true;
        }
        if (!properties.getLlm().isAutoLoad()) {
            lastError = "No chat model is loaded and app.llm.auto-load is false";
            return false;
        }
        Path cli = resolveCli();
        if (cli == null) {
            lastError = "LM Studio CLI not found. Expected %USERPROFILE%\\.cache\\lm-studio\\bin\\lms.exe";
            return false;
        }
        if (!serverReachable()) {
            int port = llmPort();
            CommandRunner.Result started =
                    commands.run(List.of(cli.toString(), "server", "start", "--port", String.valueOf(port)), Duration.ofMinutes(1));
            if (started.exitCode() != 0) {
                lastError = "Could not start LM Studio server: " + started.output().trim();
                return false;
            }
            if (!waitForServer()) {
                lastError = "LM Studio server did not become reachable on port " + port;
                return false;
            }
        }
        if (!loadedChatIds().isEmpty()) {
            return true;
        }
        Optional<String> gpuBlock = gpuBusyReason();
        if (gpuBlock.isPresent()) {
            // Too little RAM would page the model, and squeeze whatever holds the GPU.
            long freeRam = freeRamMib.getAsLong();
            int needRam = properties.getLlm().getMinFreeRamMib();
            if (freeRam < needRam) {
                lastError = gpuBlock.get() + ", and only " + freeRam + " MiB RAM free for a CPU load (need "
                        + needRam + "). Did not load the chat model.";
                return false;
            }
        }
        Optional<String> key = resolveModelKey(cli);
        if (key.isEmpty()) {
            lastError = "No Gemma chat model found on disk. lms ls --llm has no gemma model.";
            return false;
        }
        // GPU busy: run from RAM on the CPU instead. Slower, but triage has no latency need.
        String gpu = gpuBlock.isPresent() ? "off" : "max";
        log.info("Loading chat model {} (--gpu {}){}", key.get(), gpu, gpuBlock.map(r -> ": " + r).orElse(""));
        CommandRunner.Result loaded = commands.run(
                List.of(cli.toString(), "load", key.get(), "-y", "--gpu", gpu),
                Duration.ofSeconds(properties.getLlm().getLoadTimeoutSeconds()));
        if (loaded.exitCode() != 0) {
            lastError = "lms load failed: " + loaded.output().trim();
            return false;
        }
        if (!waitForChatModel()) {
            lastError = "Chat model did not become loaded after lms load (checked /api/v0/models and lms ps)";
            return false;
        }
        loadedByUs = true;
        loadedKey = key.get();
        loadedOnGpu = gpuBlock.isEmpty();
        return true;
    }

    private Optional<String> gpuBusyReason() {
        Optional<GpuOccupancy> occupancy = probeOccupancy();
        if (occupancy.isEmpty()) {
            return Optional.empty();
        }
        String reason = occupancy.get().busyReason(properties.getLlm().getMinFreeVramMib());
        return reason == null ? Optional.empty() : Optional.of(reason);
    }

    private Optional<GpuOccupancy> probeOccupancy() {
        CommandRunner.Result gpu = commands.run(
                List.of(
                        "nvidia-smi",
                        "--query-gpu=memory.used,memory.total,memory.free",
                        "--format=csv,noheader,nounits"),
                Duration.ofSeconds(10));
        if (gpu.exitCode() != 0) {
            log.warn("nvidia-smi unavailable ({}).", gpu.output().trim());
            return Optional.empty();
        }
        CommandRunner.Result compute = commands.run(
                List.of("nvidia-smi", "--query-compute-apps=pid,process_name,used_gpu_memory", "--format=csv,noheader"),
                Duration.ofSeconds(10));
        Optional<GpuOccupancy> occupancy =
                GpuOccupancy.parse(gpu.output(), compute.exitCode() == 0 ? compute.output() : "");
        if (occupancy.isEmpty()) {
            log.warn("Could not parse nvidia-smi output.");
        }
        return occupancy;
    }

    /**
     * {@code lms unload} can return before CUDA actually gives VRAM back. Poll until llama is gone
     * or give up after a minute and report the remaining occupancy.
     */
    String waitForGpuReleased() {
        long started = System.currentTimeMillis();
        long deadline = started + 60_000;
        Optional<GpuOccupancy> last = Optional.empty();
        while (System.currentTimeMillis() < deadline) {
            last = probeOccupancy();
            if (last.isEmpty() || !last.get().stillHeldAfterUnload()) {
                log.info("GPU released {} ms after unload", System.currentTimeMillis() - started);
                return null;
            }
            sleep(500);
        }
        return last.map(GpuOccupancy::heldAfterUnloadReason).orElse("nvidia-smi did not confirm GPU idle");
    }

    private Optional<String> resolveModelKey(Path cli) {
        String configured = properties.getLlm().getModelKey();
        if (configured != null && !configured.isBlank()) {
            return Optional.of(configured.trim());
        }
        CommandRunner.Result listed =
                commands.run(List.of(cli.toString(), "ls", "--json", "--llm"), Duration.ofSeconds(30));
        if (listed.exitCode() != 0) {
            lastError = "lms ls failed: " + listed.output().trim();
            return Optional.empty();
        }
        List<String> keys = parseModelKeys(listed.output());
        Optional<String> gemma4 = keys.stream().filter(k -> k.toLowerCase(Locale.ROOT).contains("gemma-4")).findFirst();
        if (gemma4.isPresent()) {
            return gemma4;
        }
        return keys.stream().filter(k -> k.toLowerCase(Locale.ROOT).contains("gemma")).findFirst();
    }

    List<String> parseModelKeys(String json) {
        List<String> keys = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return keys;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode array = root.isArray() ? root : mapper.createArrayNode().add(root);
            for (JsonNode item : array) {
                for (String field : List.of("modelKey", "key", "id", "name")) {
                    JsonNode value = item.get(field);
                    if (value != null && value.isTextual() && !value.asText().isBlank()) {
                        keys.add(value.asText());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse lms ls JSON: {}", e.getMessage());
        }
        return keys;
    }

    /**
     * Models currently loaded in memory. LM Studio's OpenAI {@code /v1/models} lists installed
     * models even when none are loaded, so this uses {@code /api/v0/models} (state=loaded) and
     * falls back to {@code lms ps}.
     */
    List<String> loadedChatIds() {
        List<String> fromApi = loadedChatIdsFromNativeApi();
        if (!fromApi.isEmpty()) {
            return fromApi;
        }
        return loadedChatIdsFromCli();
    }

    private List<String> loadedChatIdsFromNativeApi() {
        try {
            ModelsResponse response =
                    modelsClient.get().uri("/api/v0/models").retrieve().body(ModelsResponse.class);
            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(this::isLoadedChatModel)
                    .map(ModelId::id)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
        } catch (RestClientException e) {
            return List.of();
        }
    }

    private boolean isLoadedChatModel(ModelId model) {
        if (model == null || model.id() == null || model.id().isBlank()) {
            return false;
        }
        String id = model.id().toLowerCase(Locale.ROOT);
        String type = model.type() == null ? "" : model.type().toLowerCase(Locale.ROOT);
        if (id.contains("embed") || type.contains("embed")) {
            return false;
        }
        String state = model.state() == null ? "" : model.state().toLowerCase(Locale.ROOT);
        return state.equals("loaded");
    }

    private List<String> loadedChatIdsFromCli() {
        Path cli = resolveCli();
        if (cli == null) {
            return List.of();
        }
        CommandRunner.Result listed = commands.run(List.of(cli.toString(), "ps", "--json"), Duration.ofSeconds(15));
        if (listed.exitCode() != 0) {
            return List.of();
        }
        return parseModelKeys(listed.output()).stream()
                .filter(id -> id != null && !id.isBlank() && !id.toLowerCase(Locale.ROOT).contains("embed"))
                .toList();
    }

    private boolean serverReachable() {
        try {
            modelsClient.get().uri("/api/v0/models").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            try {
                modelsClient.get().uri(modelsPath()).retrieve().toBodilessEntity();
                return true;
            } catch (RestClientException ignored) {
                return false;
            }
        }
    }

    private boolean waitForServer() {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (serverReachable()) {
                return true;
            }
            sleep(500);
        }
        return false;
    }

    private boolean waitForChatModel() {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(properties.getLlm().getLoadTimeoutSeconds()).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (!loadedChatIds().isEmpty()) {
                return true;
            }
            sleep(500);
        }
        return false;
    }

    private Path resolveCli() {
        String configured = properties.getLlm().getCli();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isRegularFile(path) ? path : null;
        }
        Path windows = Path.of(System.getProperty("user.home"), ".cache", "lm-studio", "bin", "lms.exe");
        if (Files.isRegularFile(windows)) {
            return windows;
        }
        Path unix = Path.of(System.getProperty("user.home"), ".cache", "lm-studio", "bin", "lms");
        return Files.isRegularFile(unix) ? unix : null;
    }

    private int llmPort() {
        try {
            URI uri = URI.create(properties.getLlm().getBaseUrl());
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse LLM base URL: {}", e.getMessage());
        }
        return 1234;
    }

    private String modelsPath() {
        String base = properties.getLlm().getBaseUrl();
        if (base != null && base.replaceAll("/+$", "").endsWith("/v1")) {
            return "/models";
        }
        return "/v1/models";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelsResponse(List<ModelId> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelId(String id, String state, String type) {}
}
