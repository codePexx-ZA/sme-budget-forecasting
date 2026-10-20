package com.smebudget.app.targets;

import java.math.BigDecimal;

/** One KPI target from GET /api/targets. Dates stay as yyyy-MM-dd text. */
public class TargetDto {

    public String targetId;
    public String metricName;
    public BigDecimal targetValue;
    public String measurementUnit;
    public String targetPeriod;
}
