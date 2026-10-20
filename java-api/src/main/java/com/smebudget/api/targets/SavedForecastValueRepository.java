package com.smebudget.api.targets;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedForecastValueRepository extends JpaRepository<SavedForecastValue, String> {

    List<SavedForecastValue> findByRunId(String runId);
}
