package com.smebudget.api.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Holds one income-statement line sent with a budget. */
public record LineItemRequest(
        @NotBlank(message = "Each line needs a category.")
        String category,

        @NotNull(message = "Each line needs a period.")
        LocalDate periodStart,

        @NotNull(message = "Each line needs an amount.")
        BigDecimal amount,

        String frequency) {
}
