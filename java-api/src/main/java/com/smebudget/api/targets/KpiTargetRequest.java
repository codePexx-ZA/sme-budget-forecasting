package com.smebudget.api.targets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for creating or updating a KPI target. */
public record KpiTargetRequest(
        @NotBlank(message = "Metric name is required.")
        @Size(max = 100, message = "Metric name must be 100 characters or fewer.")
        String metricName,

        @NotNull(message = "Target value is required.")
        BigDecimal targetValue,

        @NotBlank(message = "Measurement unit is required.")
        @Size(max = 50, message = "Measurement unit must be 50 characters or fewer.")
        String measurementUnit,

        @NotNull(message = "Target period is required.")
        LocalDate targetPeriod
) {
}
