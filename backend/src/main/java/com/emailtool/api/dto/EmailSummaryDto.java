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
        boolean actionNeeded,
        String summary,
        Double confidence,
        Integer interestScore,
        LocalDate dueDate,
        Double blendedScore,
        Integer batchRank,
        Double ratingUrgency,
        Double ratingImportance,
        boolean injectionSuspected,
        boolean needsManualReview,
        String classificationSource,
        String gmailUrl) {}
