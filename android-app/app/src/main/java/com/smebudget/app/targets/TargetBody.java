package com.smebudget.app.targets;

/** Body for creating or updating a KPI target. */
public class TargetBody {

    public final String metricName;
    public final String targetValue;
    public final String measurementUnit;
    public final String targetPeriod;

    public TargetBody(String metricName, String targetValue, String measurementUnit, String targetPeriod) {
        this.metricName = metricName;
        this.targetValue = targetValue;
        this.measurementUnit = measurementUnit;
        this.targetPeriod = targetPeriod;
    }
}
