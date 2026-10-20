package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A row of kpi_target (created by database/init/01_schema.sql, never by this code).
 * One target is a metric, a value and a period for one business unit.
 */
@Entity
@Table(name = "kpi_target")
public class KpiTarget {

    @Id
    @Column(name = "target_id", length = 32)
    private String targetId;

    @Column(name = "unit_id", nullable = false, length = 32)
    private String unitId;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "target_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "measurement_unit", nullable = false, length = 50)
    private String measurementUnit;

    @Column(name = "target_period", nullable = false)
    private LocalDate targetPeriod;

    /** Required by JPA. */
    protected KpiTarget() {
    }

    public KpiTarget(String targetId, String unitId, String metricName, BigDecimal targetValue,
                     String measurementUnit, LocalDate targetPeriod) {
        this.targetId = targetId;
        this.unitId = unitId;
        this.metricName = metricName;
        this.targetValue = targetValue;
        this.measurementUnit = measurementUnit;
        this.targetPeriod = targetPeriod;
    }

    void update(String metricName, BigDecimal targetValue, String measurementUnit, LocalDate targetPeriod) {
        this.metricName = metricName;
        this.targetValue = targetValue;
        this.measurementUnit = measurementUnit;
        this.targetPeriod = targetPeriod;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getMetricName() {
        return metricName;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public String getMeasurementUnit() {
        return measurementUnit;
    }

    public LocalDate getTargetPeriod() {
        return targetPeriod;
    }
}
