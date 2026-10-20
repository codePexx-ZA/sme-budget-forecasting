package com.smebudget.app.targets;

import java.util.List;

/** One forecast run from GET or POST /api/insights/forecasts. */
public class ForecastDto {

    public String runId;
    public String method;
    public String historyStart;
    public String historyEnd;
    public List<String> warnings;
    public List<ForecastLineDto> values;

    public static class ForecastLineDto {
        public String displayName;
        public String period;
        public String baselineAmount;
    }
}
