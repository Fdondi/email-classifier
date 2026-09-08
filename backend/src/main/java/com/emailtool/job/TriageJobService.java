package com.emailtool.job;

import com.emailtool.classify.ClassifierJson;
import com.emailtool.classify.ClassifierJsonParser;
import com.emailtool.classify.LlmClassifier;
import com.emailtool.config.AppProperties;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import com.emailtool.ingest.IngestService;
import com.emailtool.llm.LlmRuntime;
import com.emailtool.persist.EmailRepository;
import com.emailtool.rank.RankingService;
import com.emailtool.rules.RuleEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TriageJobService {

    private static final Logger log = LoggerFactory.getLogger(TriageJobService.class);

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
            AppProperties properties) {
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
        run("schedule");
    }

    public TriageJobResult run() {
        return run("ui");
    }

    public TriageJobResult run(String source) {
        Instant started = Instant.now();
        List<String> errors = new ArrayList<>();
        IngestService.IngestStats ingestStats = new IngestService.IngestStats(0, 0, 0);
        int byRule = 0;
        int byLlm = 0;
        int fallback = 0;
        int requeued = 0;
        boolean llmReady = false;
        TriageJobResult result;
        try {
            llmReady = llmRuntime.ensureReady();
            if (!llmReady) {
                String reason = llmRuntime.lastError() == null
                        ? "Chat model is not loaded"
                        : llmRuntime.lastError();
                errors.add(reason);
                log.warn("Skipping LLM: {}", reason);
            }
            try {
                ingestStats = ingestService.ingest();
            } catch (RuntimeException e) {
                errors.add(e.getMessage());
                log.warn("Mail ingest failed: {}", e.getMessage());
            }
            requeued = requeueStaleClassifications.requeue();
            if (requeued > 0) {
                log.info("Requeued {} previously guessed emails for LLM", requeued);
            }
            List<Email> unclassified = emailRepository.findByCategoryIsNull();
            for (Email email : unclassified) {
                try {
                    if (ruleEngine.classify(email)) {
                        stampVersions(email);
                        emailRepository.save(email);
                        byRule++;
                        continue;
                    }
                    if (!llmReady) {
                        continue;
                    }
                    Optional<ClassifierJson> classified = llmClassifier.classify(email);
                    if (classified.isPresent()) {
                        applyLlm(email, classified.get(), false);
                        byLlm++;
                    } else {
                        applyLlm(email, parser.fallback(), true);
                        email.setOllamaError("LLM returned no valid JSON or was unreachable");
                        fallback++;
                        errors.add("Email " + email.getId() + " left for manual review (LLM unavailable or invalid JSON)");
                    }
                    emailRepository.save(email);
                } catch (RuntimeException e) {
                    fallback++;
                    email.setNeedsManualReview(true);
                    email.setOllamaError(e.getMessage());
                    emailRepository.save(email);
                    errors.add("Email " + email.getId() + ": " + e.getMessage());
                    log.warn("Classification failed for {}: {}", email.getId(), e.getMessage());
                }
            }
            rankingService.rankAll();
            result = new TriageJobResult(
                    ingestStats.ingested(),
                    ingestStats.skipped(),
                    ingestStats.failed(),
                    byRule,
                    byLlm,
                    fallback,
                    List.copyOf(errors));
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
        } finally {
            llmRuntime.releaseIfOwned();
        }
        lastError = result.errors().isEmpty() ? null : String.join("; ", result.errors());
        triageRunRecorder.record(source, started, Instant.now(), llmReady, requeued, result);
        return result;
    }

    private void applyLlm(Email email, ClassifierJson json, boolean fallback) {
        email.setCategory(json.category());
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
}
