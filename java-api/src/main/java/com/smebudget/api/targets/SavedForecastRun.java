package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A forecast_run row written by the Python service. This API only reads it. */
@Entity
@Table(name = "forecast_run")
public class SavedForecastRun {

    @Id
    @Column(name = "run_id", length = 32)
    private String runId;

    @Column(name = "unit_id", nullable = false, length = 32)
    private String unitId;

    @Column(name = "forecasting_method", nullable = false, length = 30)
    private String forecastingMethod;

    @Column(name = "history_start", nullable = false)
    private LocalDate historyStart;

    @Column(name = "history_end", nullable = false)
    private LocalDate historyEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedForecastRun() {
    }

    public String getRunId() {
        return runId;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getForecastingMethod() {
        return forecastingMethod;
    }

    public LocalDate getHistoryStart() {
        return historyStart;
    }

    public LocalDate getHistoryEnd() {
        return historyEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
