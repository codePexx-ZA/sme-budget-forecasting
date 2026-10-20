package com.smebudget.api.targets;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes kpi_target. The table itself is created by database/init/01_schema.sql. */
public interface KpiTargetRepository extends JpaRepository<KpiTarget, String> {

    List<KpiTarget> findByUnitIdOrderByTargetPeriodDescMetricNameAsc(String unitId);

    Optional<KpiTarget> findByUnitIdAndMetricNameAndTargetPeriod(String unitId, String metricName, LocalDate targetPeriod);
}
