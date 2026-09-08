package com.emailtool.api.dto;

import java.time.Instant;

public record CorrectionDto(
        long id,
        long emailId,
        String field,
        String predicted,
        String corrected,
        String modelVersion,
        String promptVersion,
        int taxonomyVersion,
        String classificationSource,
        Instant createdAt) {}
