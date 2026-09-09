package com.emailtool.api.dto;

import com.emailtool.domain.Category;

public record CorrectionRequest(Category category, Boolean actionNeeded) {}
