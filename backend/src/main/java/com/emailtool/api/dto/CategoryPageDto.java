package com.emailtool.api.dto;

import com.emailtool.domain.Category;
import java.util.List;

public record CategoryPageDto(Category category, long total, int offset, int limit, List<EmailSummaryDto> items) {}
