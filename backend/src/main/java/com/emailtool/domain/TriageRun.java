package com.emailtool.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "triage_run")
public class TriageRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriageRunOutcome outcome;

    @Column(nullable = false)
    private int ingested;

    @Column(nullable = false)
    private int skipped;

    @Column(name = "ingest_failed", nullable = false)
    private int ingestFailed;

    @Column(nullable = false)
    private int requeued;

    @Column(name = "classified_by_rule", nullable = false)
    private int classifiedByRule;

    @Column(name = "classified_by_llm", nullable = false)
    private int classifiedByLlm;

    @Column(nullable = false)
    private int fallback;

    @Column(name = "unclassified_left", nullable = false)
    private int unclassifiedLeft;

    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "llm_ready", nullable = false)
    private boolean llmReady;

    private String errors;

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public TriageRunOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TriageRunOutcome outcome) {
        this.outcome = outcome;
    }

    public int getIngested() {
        return ingested;
    }

    public void setIngested(int ingested) {
        this.ingested = ingested;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getIngestFailed() {
        return ingestFailed;
    }

    public void setIngestFailed(int ingestFailed) {
        this.ingestFailed = ingestFailed;
    }

    public int getRequeued() {
        return requeued;
    }

    public void setRequeued(int requeued) {
        this.requeued = requeued;
    }

    public int getClassifiedByRule() {
        return classifiedByRule;
    }

    public void setClassifiedByRule(int classifiedByRule) {
        this.classifiedByRule = classifiedByRule;
    }

    public int getClassifiedByLlm() {
        return classifiedByLlm;
    }

    public void setClassifiedByLlm(int classifiedByLlm) {
        this.classifiedByLlm = classifiedByLlm;
    }

    public int getFallback() {
        return fallback;
    }

    public void setFallback(int fallback) {
        this.fallback = fallback;
    }

    public int getUnclassifiedLeft() {
        return unclassifiedLeft;
    }

    public void setUnclassifiedLeft(int unclassifiedLeft) {
        this.unclassifiedLeft = unclassifiedLeft;
    }

    public boolean isLlmReady() {
        return llmReady;
    }

    public void setLlmReady(boolean llmReady) {
        this.llmReady = llmReady;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }
}
