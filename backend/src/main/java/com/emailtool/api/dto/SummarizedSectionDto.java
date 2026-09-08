package com.emailtool.api.dto;

import java.util.List;

public record SummarizedSectionDto(List<EmailSummaryDto> top, long total) {}
