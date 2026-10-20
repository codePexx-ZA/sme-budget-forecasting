package com.smebudget.api.targets;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One KPI target, sent to the Android app. */
public record KpiTargetResponse(
        String targetId,
        String metricName,
        BigDecimal targetValue,
        String measurementUnit,
        LocalDate targetPeriod
) {

    static KpiTargetResponse from(KpiTarget target) {
        return new KpiTargetResponse(
                target.getTargetId(),
                target.getMetricName(),
                target.getTargetValue(),
                target.getMeasurementUnit(),
                target.getTargetPeriod());
    }
}
