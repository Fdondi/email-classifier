package com.emailtool.api.dto;

import com.emailtool.domain.Category;
import java.time.Instant;
import java.time.LocalDate;

public record EmailSummaryDto(
        long id,
        String subject,
        String fromAddress,
        String fromDisplayName,
        Instant dateSent,
        Category category,
        String summary,
        Double confidence,
        Integer interestScore,
        LocalDate dueDate,
        Double blendedScore,
        Integer batchRank,
        boolean injectionSuspected,
        boolean needsManualReview,
        String classificationSource,
        String gmailUrl) {}
