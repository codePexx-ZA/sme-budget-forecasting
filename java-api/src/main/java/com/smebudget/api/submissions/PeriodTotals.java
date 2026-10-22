package com.smebudget.api.submissions;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Holds the calculated gross, operating, and net profit for one period. */
public record PeriodTotals(
        LocalDate periodStart,
        BigDecimal grossProfit,
        BigDecimal operatingProfit,
        BigDecimal netProfit) {
}
