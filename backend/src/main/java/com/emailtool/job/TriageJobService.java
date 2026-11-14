package com.emailtool.job;

import com.emailtool.api.dto.TriageProgressDto;
import com.emailtool.classify.ClassifierJson;
import com.emailtool.classify.ClassifierJsonParser;
import com.emailtool.classify.LlmClassifier;
import com.emailtool.config.AppProperties;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import com.emailtool.ingest.IngestService;
import com.emailtool.jobs.JobCandidateService;
import com.emailtool.llm.LlmRuntime;
import com.emailtool.persist.EmailRepository;
import com.emailtool.rank.RankingService;
import com.emailtool.rules.RuleEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TriageJobService {

    private static final Logger log = LoggerFactory.getLogger(TriageJobService.class);

    public static final String PHASE_IDLE = "IDLE";
    public static final String PHASE_STARTING_LLM = "STARTING_LLM";
    public static final String PHASE_DOWNLOADING = "DOWNLOADING";
    public static final String PHASE_CLASSIFYING = "CLASSIFYING";
    public static final String PHASE_RANKING = "RANKING";
    public static final String PHASE_JOBS = "JOBS";
    public static final String PHASE_DONE = "DONE";
    public static final String PHASE_FAILED = "FAILED";

    private final IngestService ingestService;
    private final RuleEngine ruleEngine;
    private final LlmClassifier llmClassifier;
    private final ClassifierJsonParser parser;
    private final EmailRepository emailRepository;
    private final RankingService rankingService;
    private final LlmRuntime llmRuntime;
    private final RequeueStaleClassifications requeueStaleClassifications;
    private final TriageRunRecorder triageRunRecorder;
    private final AppProperties properties;
    private final JobCandidateService jobCandidateService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final LiveProgress live = new LiveProgress();
    private volatile String lastError;

    public TriageJobService(
            IngestService ingestService,
            RuleEngine ruleEngine,
            LlmClassifier llmClassifier,
            ClassifierJsonParser parser,
            EmailRepository emailRepository,
            RankingService rankingService,
            LlmRuntime llmRuntime,
            RequeueStaleClassifications requeueStaleClassifications,
            TriageRunRecorder triageRunRecorder,
            AppProperties properties,
            JobCandidateService jobCandidateService) {
        this.jobCandidateService = jobCandidateService;
        this.ingestService = ingestService;
        this.ruleEngine = ruleEngine;
        this.llmClassifier = llmClassifier;
        this.parser = parser;
        this.emailRepository = emailRepository;
        this.rankingService = rankingService;
        this.llmRuntime = llmRuntime;
        this.requeueStaleClassifications = requeueStaleClassifications;
        this.triageRunRecorder = triageRunRecorder;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.schedule-cron:0 30 3 * * *}")
    public void scheduledRun() {
        if (!properties.isScheduleEnabled()) {
            return;
        }
        if (running.get()) {
            log.info("Skipping scheduled triage; already running");
            return;
        }
        try {
            run("schedule");
        } catch (IllegalStateException e) {
            log.info("Skipping scheduled triage: {}", e.getMessage());
        }
    }

    /** Start triage in the background. Returns current progress immediately. */
    public TriageProgressDto startAsync(String source) {
        if (!running.compareAndSet(false, true)) {
            return progress();
        }
        live.begin();
        Thread.ofVirtual().name("triage-job").start(() -> {
            try {
                runBody(source == null || source.isBlank() ? "ui" : source);
            } finally {
                running.set(false);
            }
        });
        return progress();
    }

    public TriageProgressDto progress() {
        return live.snapshot(running.get());
    }

    public TriageJobResult run() {
        return run("ui");
    }

    public TriageJobResult run(String source) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Triage is already running");
        }
        live.begin();
        try {
            return runBody(source == null || source.isBlank() ? "ui" : source);
        } finally {
            running.set(false);
        }
    }

    private TriageJobResult runBody(String source) {
        Instant started = Instant.now();
        List<String> errors = new ArrayList<>();
        IngestService.IngestStats ingestStats = new IngestService.IngestStats(0, 0, 0);
        int byRule = 0;
        int byLlm = 0;
        int fallback = 0;
        int requeued = 0;
        boolean llmReady = false;
        AtomicReference<Instant> loadingStartedAt = new AtomicReference<>();
        AtomicReference<Instant> loadingEndedAt = new AtomicReference<>();
        Instant downloadingStartedAt = null;
        Instant downloadingEndedAt = null;
        List<Long> emailProcessingMs = new ArrayList<>();
        TriageJobResult result;
        try {
            // Mail download and GPU load are independent; overlap them so triage waits only
            // for the slower of the two before classification.
            live.setPhase(PHASE_STARTING_LLM);
            CompletableFuture<Boolean> llmFuture = CompletableFuture.supplyAsync(
                    () -> {
                        loadingStartedAt.set(Instant.now());
                        try {
                            return llmRuntime.ensureReady();
                        } finally {
                            loadingEndedAt.set(Instant.now());
                        }
                    },
                    task -> Thread.ofVirtual().name("triage-llm-load").start(task));
            try {
                live.setPhase(PHASE_DOWNLOADING);
                downloadingStartedAt = Instant.now();
                ingestStats = ingestService.ingest((ingested, skipped, failed) ->
                        live.setIngest(ingested, skipped, failed));
                live.setIngest(ingestStats.ingested(), ingestStats.skipped(), ingestStats.failed());
            } catch (RuntimeException e) {
                errors.add(e.getMessage());
                live.setErrors(errors);
                log.warn("Mail ingest failed: {}", e.getMessage());
            } finally {
                downloadingEndedAt = Instant.now();
            }
            try {
                if (!llmFuture.isDone()) {
                    live.setPhase(PHASE_STARTING_LLM);
                }
                llmReady = llmFuture.join();
                if (!llmReady) {
                    String reason = llmRuntime.lastError() == null
                            ? "Chat model is not loaded"
                            : llmRuntime.lastError();
                    errors.add(reason);
                    live.setErrors(errors);
                    log.warn("Skipping LLM: {}", reason);
                }
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String reason = cause.getMessage() == null
                        ? cause.getClass().getSimpleName()
                        : cause.getMessage();
                errors.add(reason);
                live.setErrors(errors);
                log.warn("Skipping LLM: {}", reason);
                llmReady = false;
            }
            requeued = requeueStaleClassifications.requeue();
            if (requeued > 0) {
                log.info("Requeued {} previously guessed emails for LLM", requeued);
            }
            live.setPhase(PHASE_CLASSIFYING);
            List<Email> unclassified = emailRepository.findByCategoryIsNull();
            int toClassify = 0;
            for (Email email : unclassified) {
                if (email.isDigestVisible()) {
                    toClassify++;
                }
            }
            live.setToClassify(toClassify);
            List<String> runErrors = new ArrayList<>(errors);
            List<String> classificationErrors = new ArrayList<>();
            int llmAttempts = 0;
            for (Email email : unclassified) {
                if (!email.isDigestVisible()) {
                    continue;
                }
                long emailStartedNanos = System.nanoTime();
                try {
                    if (ruleEngine.classify(email)) {
                        stampVersions(email);
                        emailRepository.save(email);
                        byRule++;
                        live.setClassified(byRule, byLlm, fallback);
                        continue;
                    }
                    if (!llmReady) {
                        continue;
                    }
                    llmAttempts++;
                    Optional<ClassifierJson> classified = llmClassifier.classify(email);
                    if (classified.isPresent()) {
                        applyLlm(email, classified.get(), false);
                        byLlm++;
                    } else {
                        applyLlm(email, parser.fallback(), true);
                        email.setOllamaError("LLM returned no valid JSON or was unreachable");
                        fallback++;
                        classificationErrors.add(
                                "Email " + email.getId() + " left for manual review (LLM unavailable or invalid JSON)");
                        live.setErrors(collapsedErrors(runErrors, classificationErrors, llmAttempts));
                    }
                    emailRepository.save(email);
                    live.setClassified(byRule, byLlm, fallback);
                } catch (RuntimeException e) {
                    fallback++;
                    email.setNeedsManualReview(true);
                    email.setOllamaError(e.getMessage());
                    emailRepository.save(email);
                    classificationErrors.add("Email " + email.getId() + ": " + e.getMessage());
                    live.setClassified(byRule, byLlm, fallback);
                    live.setErrors(collapsedErrors(runErrors, classificationErrors, llmAttempts));
                    log.warn("Classification failed for {}: {}", email.getId(), e.getMessage());
                } finally {
                    emailProcessingMs.add((System.nanoTime() - emailStartedNanos) / 1_000_000L);
                }
            }
            errors.clear();
            errors.addAll(collapsedErrors(runErrors, classificationErrors, llmAttempts));
            live.setErrors(errors);
            live.setPhase(PHASE_RANKING);
            rankingService.rankAll();
            if (jobCandidateService.isEnabled()) {
                // While the model is still loaded: read job mail for postings, propose the best to letter-writer.
                live.setPhase(PHASE_JOBS);
                try {
                    errors.addAll(jobCandidateService.run(llmReady, Instant.now()).errors());
                } catch (RuntimeException e) {
                    errors.add("Job candidates: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    log.warn("Job candidates failed: {}", e.getMessage());
                }
                live.setErrors(errors);
            }
            result = new TriageJobResult(
                    ingestStats.ingested(),
                    ingestStats.skipped(),
                    ingestStats.failed(),
                    byRule,
                    byLlm,
                    fallback,
                    List.copyOf(errors));
            live.finish(PHASE_DONE, result);
        } catch (RuntimeException e) {
            errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.warn("Triage run failed: {}", e.getMessage());
            result = new TriageJobResult(
                    ingestStats.ingested(),
                    ingestStats.skipped(),
                    ingestStats.failed(),
                    byRule,
                    byLlm,
                    fallback,
                    List.copyOf(errors));
            live.finish(PHASE_FAILED, result);
        } finally {
            llmRuntime.releaseIfOwned();
        }
        logRunTiming(
                loadingStartedAt.get(),
                loadingEndedAt.get(),
                downloadingStartedAt,
                downloadingEndedAt,
                emailProcessingMs);
        lastError = result.errors().isEmpty() ? null : String.join("; ", result.errors());
        triageRunRecorder.record(source, started, Instant.now(), llmReady, requeued, result);
        return result;
    }

    private static void logRunTiming(
            Instant loadingStartedAt,
            Instant loadingEndedAt,
            Instant downloadingStartedAt,
            Instant downloadingEndedAt,
            List<Long> emailProcessingMs) {
        log.info(
                "Loading started at {}, ended at {}",
                loadingStartedAt == null ? "n/a" : loadingStartedAt,
                loadingEndedAt == null ? "n/a" : loadingEndedAt);
        log.info(
                "Downloading started at {}, ended at {}",
                downloadingStartedAt == null ? "n/a" : downloadingStartedAt,
                downloadingEndedAt == null ? "n/a" : downloadingEndedAt);
        if (emailProcessingMs.isEmpty()) {
            log.info("Email processing: no emails classified");
            return;
        }
        long averageMs = averageMs(emailProcessingMs);
        long p90Ms = percentileMs(emailProcessingMs, 0.90);
        log.info(
                "Email processing average {} ms, 90th percentile slowest {} ms (n={})",
                averageMs,
                p90Ms,
                emailProcessingMs.size());
    }

    static long averageMs(List<Long> samplesMs) {
        if (samplesMs.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (long sample : samplesMs) {
            sum += sample;
        }
        return Math.round(sum / (double) samplesMs.size());
    }

    /** Nearest-rank percentile over millisecond samples (p in (0, 1]). */
    static long percentileMs(List<Long> samplesMs, double percentile) {
        if (samplesMs.isEmpty()) {
            return 0L;
        }
        if (percentile <= 0 || percentile > 1) {
            throw new IllegalArgumentException("percentile must be in (0, 1], got " + percentile);
        }
        long[] sorted = samplesMs.stream().mapToLong(Long::longValue).sorted().toArray();
        int rank = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(rank, sorted.length - 1))];
    }

    private void applyLlm(Email email, ClassifierJson json, boolean fallback) {
        email.setCategory(json.category());
        email.setActionNeeded(Boolean.TRUE.equals(json.actionNeeded()));
        email.setConfidence(json.confidence());
        email.setSummary(json.summary());
        email.setInterestScore(json.interestScore());
        email.setDueDate(json.dueDate());
        email.setInjectionSuspected(Boolean.TRUE.equals(json.injectionSuspected()));
        if (json.language() != null) {
            email.setLanguage(json.language());
        }
        email.setClassificationSource(fallback ? ClassificationSource.FALLBACK : ClassificationSource.LLM);
        email.setNeedsManualReview(fallback);
        email.setClassifiedAt(Instant.now());
        email.setModelVersion(llmClassifier.modelName());
        stampVersions(email);
        if (!fallback) {
            email.setOllamaError(null);
        }
    }

    private void stampVersions(Email email) {
        email.setPromptVersion(properties.getPromptVersion());
        email.setTaxonomyVersion(properties.getTaxonomyVersion());
    }

    /**
     * When every LLM attempt fails, keep a single summary line instead of one row per email.
     */
    static List<String> collapsedErrors(List<String> prefix, List<String> classificationErrors, int llmAttempts) {
        List<String> out = new ArrayList<>(prefix);
        if (classificationErrors.isEmpty()) {
            return out;
        }
        if (llmAttempts > 0 && classificationErrors.size() == llmAttempts) {
            String shared = sharedFailureDetail(classificationErrors);
            if (shared != null) {
                out.add("All " + llmAttempts + " emails failed: " + shared);
            } else {
                out.add("All " + llmAttempts + " emails failed LLM classification");
            }
            return out;
        }
        out.addAll(classificationErrors);
        return out;
    }

    private static String sharedFailureDetail(List<String> classificationErrors) {
        String firstDetail = failureDetail(classificationErrors.getFirst());
        for (String line : classificationErrors) {
            if (!firstDetail.equals(failureDetail(line))) {
                return null;
            }
        }
        return firstDetail;
    }

    private static String failureDetail(String line) {
        int colon = line.indexOf(": ");
        if (colon >= 0 && line.startsWith("Email ")) {
            return line.substring(colon + 2);
        }
        String marker = " left for manual review ";
        int at = line.indexOf(marker);
        if (at >= 0) {
            return line.substring(at + 1).trim();
        }
        return line;
    }

    public String lastError() {
        return lastError;
    }

    public record TriageJobResult(
            int ingested,
            int skipped,
            int ingestFailed,
            int classifiedByRule,
            int classifiedByLlm,
            int fallback,
            List<String> errors) {}

    private static final class LiveProgress {
        private volatile String phase = PHASE_IDLE;
        private volatile Instant startedAt;
        private volatile Instant finishedAt;
        private final AtomicInteger ingested = new AtomicInteger();
        private final AtomicInteger skipped = new AtomicInteger();
        private final AtomicInteger ingestFailed = new AtomicInteger();
        private final AtomicInteger classifiedByRule = new AtomicInteger();
        private final AtomicInteger classifiedByLlm = new AtomicInteger();
        private final AtomicInteger fallback = new AtomicInteger();
        private final AtomicInteger toClassify = new AtomicInteger();
        private volatile List<String> errors = List.of();

        void begin() {
            phase = PHASE_STARTING_LLM;
            startedAt = Instant.now();
            finishedAt = null;
            ingested.set(0);
            skipped.set(0);
            ingestFailed.set(0);
            classifiedByRule.set(0);
            classifiedByLlm.set(0);
            fallback.set(0);
            toClassify.set(0);
            errors = List.of();
        }

        void setPhase(String next) {
            phase = next;
        }

        void setIngest(int ingestedCount, int skippedCount, int failedCount) {
            ingested.set(ingestedCount);
            skipped.set(skippedCount);
            ingestFailed.set(failedCount);
        }

        void setToClassify(int total) {
            toClassify.set(total);
        }

        void setClassified(int byRule, int byLlm, int fallbackCount) {
            classifiedByRule.set(byRule);
            classifiedByLlm.set(byLlm);
            fallback.set(fallbackCount);
        }

        void setErrors(List<String> next) {
            errors = List.copyOf(next);
        }

        void finish(String endPhase, TriageJobResult result) {
            phase = endPhase;
            finishedAt = Instant.now();
            ingested.set(result.ingested());
            skipped.set(result.skipped());
            ingestFailed.set(result.ingestFailed());
            classifiedByRule.set(result.classifiedByRule());
            classifiedByLlm.set(result.classifiedByLlm());
            fallback.set(result.fallback());
            errors = List.copyOf(result.errors());
        }

        TriageProgressDto snapshot(boolean isRunning) {
            return new TriageProgressDto(
                    isRunning,
                    phase,
                    startedAt,
                    finishedAt,
                    ingested.get(),
                    skipped.get(),
                    ingestFailed.get(),
                    classifiedByRule.get(),
                    classifiedByLlm.get(),
                    fallback.get(),
                    toClassify.get(),
                    errors);
        }
    }
}
