package com.smebudget.api.submissions;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Holds one saved budget line returned by the API. */
public record LineItemResponse(
        String lineItemId,
        String category,
        String categoryName,
        LocalDate periodStart,
        BigDecimal amount,
        String frequency) {
}
