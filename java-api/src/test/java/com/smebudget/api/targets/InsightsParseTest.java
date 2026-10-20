package com.smebudget.api.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Checks that Python and C# JSON is rewritten into the fields the Android app reads. */
class InsightsParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsAForecastRun() throws Exception {
        JsonNode run = mapper.readTree("""
                {
                  "run_id": "abc",
                  "forecasting_method": "moving_average",
                  "history_start": "2021-03-01",
                  "history_end": "2026-02-01",
                  "parameters": {"warnings": ["Short history"]},
                  "values": [{
                    "category": "revenue",
                    "display_name": "Revenue",
                    "forecast_period": "2027-03-01",
                    "baseline_amount": "100.50"
                  }]
                }
                """);

        ForecastView view = InsightsService.toForecast(run);

        assertEquals("abc", view.runId());
        assertEquals("moving_average", view.method());
        assertEquals("Short history", view.warnings().get(0));
        assertEquals("Revenue", view.values().get(0).displayName());
        assertEquals("100.50", view.values().get(0).baselineAmount());
    }

    @Test
    void readsAnAlert() throws Exception {
        JsonNode row = mapper.readTree("""
                {"severity": "warning", "message": "Fuel is up", "resolutionStatus": "open"}
                """);

        AlertView alert = InsightsService.toAlert(row);

        assertEquals("warning", alert.severity());
        assertEquals("Fuel is up", alert.message());
        assertEquals("open", alert.resolutionStatus());
    }
}
