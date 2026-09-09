package com.emailtool.api.dto;

import java.util.List;

public record DualAxisSectionDto(
        List<EmailSummaryDto> byUrgency,
        List<EmailSummaryDto> byImportance,
        List<EmailSummaryDto> allEmails,
        long total) {}
