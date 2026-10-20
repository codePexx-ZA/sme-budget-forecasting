package com.smebudget.api.targets;

import java.util.List;

/** One saved forecast run, rewritten from the Python service into the shape the app expects. */
public record ForecastView(
        String runId,
        String method,
        String historyStart,
        String historyEnd,
        List<String> warnings,
        List<ForecastLineView> values
) {
}
