package com.emailtool.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RatingRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double urgency,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double importance,
        Double predictedUrgency,
        Double predictedImportance) {}
