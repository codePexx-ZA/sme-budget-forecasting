package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** One line sent with a submission. category is the category code, not the display name. */
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
