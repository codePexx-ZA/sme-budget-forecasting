package com.smebudget.api.submissions;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A saved line. amount is rands. */
public record LineItemResponse(
        String lineItemId,
        String category,
        String categoryName,
        LocalDate periodStart,
        BigDecimal amount,
        String frequency) {
}
