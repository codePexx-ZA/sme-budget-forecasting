package com.smebudget.api.targets;

/** One forecast amount for a category and period. Amounts stay as text so rounding is unchanged. */
public record ForecastLineView(
        String category,
        String displayName,
        String period,
        String baselineAmount
) {
}
