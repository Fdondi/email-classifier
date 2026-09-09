package com.emailtool.api.dto;

import java.util.List;

public record DigestDto(
        List<EmailSummaryDto> quarantine,
        DualAxisSectionDto actionNeeded,
        DualAxisSectionDto events,
        DualAxisSectionDto jobOffers,
        SummarizedSectionDto promotions,
        SummarizedSectionDto newslettersTech,
        SummarizedSectionDto newslettersOther,
        SummarizedSectionDto other,
        long spamCount,
        long hiddenCount,
        String lastJobError) {}
