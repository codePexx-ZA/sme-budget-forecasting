package com.smebudget.app.submissions;

import java.math.BigDecimal;

/** A saved line. amount is rands. */
public class LineItemDto {

    public String lineItemId;
    public String category;
    public String categoryName;
    public String periodStart;
    public BigDecimal amount;
    public String frequency;
}
