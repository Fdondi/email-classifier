package com.emailtool.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ListRankRequest(@NotEmpty List<Long> ids) {}
