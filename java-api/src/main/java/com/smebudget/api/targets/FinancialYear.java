package com.smebudget.api.targets;

import java.time.LocalDate;

/**
 * Turns a business year-end month into the financial year that contains a day,
 * and the year after it. February (2) runs from 1 March to 28 or 29 February.
 * The date rules live here so the submission form and the tests share one place.
 */
final class FinancialYear {

    static final int DEFAULT_YEAR_END_MONTH = 2;

    private FinancialYear() {
    }

    /** Keeps 1 to 12. Anything else, including a missing business row, is February. */
    static int month(int yearEndMonth) {
        if (yearEndMonth < 1 || yearEndMonth > 12) {
            return DEFAULT_YEAR_END_MONTH;
        }
        return yearEndMonth;
    }

    /**
     * The financial year that contains {@code day}. It ends on the last day of
     * {@code yearEndMonth} (29 February when that year is a leap year).
     */
    static Span containing(LocalDate day, int yearEndMonth) {
        int endMonth = month(yearEndMonth);
        int endYear = day.getMonthValue() > endMonth ? day.getYear() + 1 : day.getYear();
        // Last day of the year-end month, so February becomes 28 or 29.
        LocalDate end = LocalDate.of(endYear, endMonth, 1).plusMonths(1).minusDays(1);
        LocalDate start = end.minusYears(1).plusMonths(1).withDayOfMonth(1);
        return new Span(start, end);
    }

    /** The next financial year. 1 March 2027 through 29 February 2028 when this year ends in February 2027. */
    static Span following(Span current) {
        LocalDate start = current.start().plusYears(1);
        return new Span(start, start.plusYears(1).minusDays(1));
    }

    record Span(LocalDate start, LocalDate end) {
    }
}
