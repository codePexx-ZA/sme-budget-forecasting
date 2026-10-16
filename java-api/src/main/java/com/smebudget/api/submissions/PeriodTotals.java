package com.smebudget.api.submissions;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Subtotals for one period. They are calculated for display and are not stored.
 *
 * Gross profit = revenue + other income − cost of sales.
 * Operating profit = gross profit − employee costs − occupancy − electricity − other utilities
 * − transport and fuel − marketing − other operating expenses − depreciation.
 * Net profit = operating profit − finance costs − income tax.
 * A category that is missing for the period counts as zero. Expenses are positive amounts.
 */
public record PeriodTotals(
        LocalDate periodStart,
        BigDecimal grossProfit,
        BigDecimal operatingProfit,
        BigDecimal netProfit) {
}
