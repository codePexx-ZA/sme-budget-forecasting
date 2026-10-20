package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One forecast_value row. Amounts are rands. This API only reads them. */
@Entity
@Table(name = "forecast_value")
public class SavedForecastValue {

    @Id
    @Column(name = "value_id", length = 32)
    private String valueId;

    @Column(name = "run_id", nullable = false, length = 32)
    private String runId;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "forecast_period", nullable = false)
    private LocalDate forecastPeriod;

    @Column(name = "baseline_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal baselineAmount;

    protected SavedForecastValue() {
    }

    public String getRunId() {
        return runId;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getForecastPeriod() {
        return forecastPeriod;
    }

    public BigDecimal getBaselineAmount() {
        return baselineAmount;
    }
}
