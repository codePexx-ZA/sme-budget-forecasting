package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** Gross, operating and net profit for one period. Calculated by the API, not stored. */
public class PeriodTotalsDto {

    public String periodStart;
    public BigDecimal grossProfit;
    public BigDecimal operatingProfit;
    public BigDecimal netProfit;
}
