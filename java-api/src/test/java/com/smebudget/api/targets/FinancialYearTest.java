package com.smebudget.api.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** February year-end is 1 March through 28 or 29 February. Other months follow the same rule. */
class FinancialYearTest {

    @Test
    void octoberSitsInTheYearThatEndsNextFebruary() {
        FinancialYear.Span current = FinancialYear.containing(LocalDate.parse("2026-10-21"), 2);

        assertEquals(LocalDate.parse("2026-03-01"), current.start());
        assertEquals(LocalDate.parse("2027-02-28"), current.end());
    }

    @Test
    void theFollowingFebruaryYearIncludesTheLeapDay() {
        FinancialYear.Span current = FinancialYear.containing(LocalDate.parse("2026-10-21"), 2);
        FinancialYear.Span next = FinancialYear.following(current);

        assertEquals(LocalDate.parse("2027-03-01"), next.start());
        assertEquals(LocalDate.parse("2028-02-29"), next.end());
    }

    @Test
    void aDayInFebruaryStillBelongsToTheYearThatEndsThatMonth() {
        FinancialYear.Span current = FinancialYear.containing(LocalDate.parse("2026-02-15"), 2);

        assertEquals(LocalDate.parse("2025-03-01"), current.start());
        assertEquals(LocalDate.parse("2026-02-28"), current.end());
    }

    @Test
    void aLeapDayYearEndIsKept() {
        FinancialYear.Span current = FinancialYear.containing(LocalDate.parse("2023-10-01"), 2);

        assertEquals(LocalDate.parse("2023-03-01"), current.start());
        assertEquals(LocalDate.parse("2024-02-29"), current.end());
    }

    @Test
    void aMissingOrInvalidMonthFallsBackToFebruary() {
        FinancialYear.Span missing = FinancialYear.containing(LocalDate.parse("2026-10-21"), 0);
        FinancialYear.Span tooHigh = FinancialYear.containing(LocalDate.parse("2026-10-21"), 13);

        assertEquals(LocalDate.parse("2026-03-01"), missing.start());
        assertEquals(LocalDate.parse("2027-02-28"), tooHigh.end());
        assertEquals(2, FinancialYear.month(0));
    }

    @Test
    void aDecemberYearEndRunsFromJanuary() {
        FinancialYear.Span current = FinancialYear.containing(LocalDate.parse("2026-01-15"), 12);

        assertEquals(LocalDate.parse("2026-01-01"), current.start());
        assertEquals(LocalDate.parse("2026-12-31"), current.end());
        assertEquals(LocalDate.parse("2027-01-01"), FinancialYear.following(current).start());
        assertEquals(LocalDate.parse("2027-12-31"), FinancialYear.following(current).end());
    }
}
