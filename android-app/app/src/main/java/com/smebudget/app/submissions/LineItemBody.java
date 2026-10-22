package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** Holds one budget line sent with a submission. */
public class LineItemBody {

    final String category;
    final String periodStart;
    final BigDecimal amount;
    final String frequency;

    public LineItemBody(String category, String periodStart, BigDecimal amount, String frequency) {
        this.category = category;
        this.periodStart = periodStart;
        this.amount = amount;
        this.frequency = frequency;
    }
}
