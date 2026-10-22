package com.smebudget.api.targets;

import java.time.LocalDate;

/**
 * Dates for the two financial-year chips on the submission form.
 * {@code yearEndMonth} is the business row's year-end (2 = February).
 */
public record FinancialYearResponse(
        int yearEndMonth,
        LocalDate thisStart,
        LocalDate thisEnd,
        LocalDate nextStart,
        LocalDate nextEnd) {
}
