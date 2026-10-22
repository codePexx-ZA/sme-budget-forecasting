package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** Holds the gross, operating, and net profit for one period. */
public class PeriodTotalsDto {

    public String periodStart;
    public BigDecimal grossProfit;
    public BigDecimal operatingProfit;
    public BigDecimal netProfit;
}
