package com.emailtool.api.dto;

import com.emailtool.domain.Category;
import java.time.Instant;
import java.time.LocalDate;

public record EmailDetailDto(
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
        boolean injectionSuspected,
        boolean needsManualReview,
        boolean imagesLoaded,
        int remoteImageCount,
        String classificationSource,
        String language,
        String messageId,
        boolean userHidden,
        String gmailUrl) {}
