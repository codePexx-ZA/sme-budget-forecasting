package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** Holds one saved budget line, with the amount in rands. */
public class LineItemDto {

    public String lineItemId;
    public String category;
    public String categoryName;
    public String periodStart;
    public BigDecimal amount;
    public String frequency;
}
