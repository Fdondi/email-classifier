package com.emailtool.api.dto;

import com.emailtool.domain.Category;
import jakarta.validation.constraints.NotNull;

public record CorrectionRequest(@NotNull Category category) {}
