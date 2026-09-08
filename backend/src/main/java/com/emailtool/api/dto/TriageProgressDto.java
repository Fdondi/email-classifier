package com.emailtool.api.dto;

import java.time.Instant;
import java.util.List;

public record TriageProgressDto(
        boolean running,
        String phase,
        Instant startedAt,
        Instant finishedAt,
        int ingested,
        int skipped,
        int ingestFailed,
        int classifiedByRule,
        int classifiedByLlm,
        int fallback,
        int toClassify,
        List<String> errors) {}
