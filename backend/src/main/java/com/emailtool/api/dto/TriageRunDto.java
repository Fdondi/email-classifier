package com.emailtool.api.dto;

import java.time.Instant;
import java.util.List;

public record TriageRunDto(
        long id,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        String source,
        String outcome,
        int ingested,
        int skipped,
        int ingestFailed,
        int requeued,
        int classifiedByRule,
        int classifiedByLlm,
        int fallback,
        int unclassifiedLeft,
        boolean llmReady,
        List<String> errors) {}
