package com.smebudget.api.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smebudget.api.submissions.BudgetSubmission;
import com.smebudget.api.submissions.FinancialLineItem;
import com.smebudget.api.submissions.LineCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The report keeps the newest approved annual year and drops anything not yet approved. */
class ReportServiceTest {

    @Test
    void latestApprovedYearWinsAndDraftsAreIgnored() {
        BudgetSubmission older = submission("old", "approved", Instant.parse("2026-01-01T00:00:00Z"));
        BudgetSubmission newer = submission("new", "approved", Instant.parse("2026-06-01T00:00:00Z"));
        BudgetSubmission draft = submission("draft", "draft", null);
        FinancialLineItem oldRevenue = line("old", "revenue", "2025-03-01", "100");
        FinancialLineItem replaced = line("old", "revenue", "2024-03-01", "50");
        FinancialLineItem newRevenue = line("new", "revenue", "2024-03-01", "80");
        FinancialLineItem monthly = line("new", "revenue", "2025-03-01", "9");
        monthly.setFrequency(FinancialLineItem.MONTHLY);

        FinancialLineItem draftRevenue = line("draft", "revenue", "2025-03-01", "999999");
        ReportService.LatestYear year = ReportService.selectLatestYear(
                List.of(older, newer, draft),
                Map.of(
                        "old", List.of(oldRevenue, replaced),
                        "new", List.of(newRevenue, monthly),
                        "draft", List.of(draftRevenue)));

        assertEquals(LocalDate.parse("2025-03-01"), year.period());
        assertEquals(new BigDecimal("100"), year.amounts().get("revenue"));
        assertEquals(1, year.amounts().size());
    }

    @Test
    void shareTextCarriesTheSameSectionsTheScreenShows() {
        MeetingReport report = new MeetingReport(
                "Sample Hardware Store",
                "Whole company",
                "Dana Decision Maker (Test)",
                "20 Oct 2026, 00:20",
                "Latest approved year starts 2025-03-01.",
                "No forecast has been run yet.",
                "Not in these figures: 1 submission is waiting for approval.",
                List.of(new MeetingReport.ApprovalNote("Alex Approver (Test)", "12 Apr 2026", "2021-03-01 to 2026-02-28", "")),
                List.of(new MeetingReport.ComparisonRow("Revenue", "R 5,420,000.00", "n/a", "n/a", null, null)),
                List.of(new MeetingReport.Fact("Gross profit", "R 2,000,000.00", null)),
                List.of(new MeetingReport.TargetLine("Revenue", "R 5,800,000.00", "2026-03-01", null, null, null, null, null)),
                List.of(new MeetingReport.WarningLine("warning", "Fuel is up")),
                "");

        String text = ReportService.shareText(report);

        assertTrue(text.contains("Sample Hardware Store"));
        assertTrue(text.contains("Approved by Alex Approver"));
        assertTrue(text.contains("Revenue"));
        assertTrue(text.contains("R 5,420,000.00"));
        assertTrue(text.contains("Fuel is up"));
        assertTrue(text.contains("waiting for approval"));
    }

    @Test
    void forecastToneTreatsAHigherCostAsWorse() {
        BigDecimal last = new BigDecimal("100");
        BigDecimal higher = new BigDecimal("120");
        BigDecimal lower = new BigDecimal("80");

        assertEquals("favorable", ReportService.forecastTone(higher, last, true));
        assertEquals("unfavorable", ReportService.forecastTone(lower, last, true));
        assertEquals("unfavorable", ReportService.forecastTone(higher, last, false));
        assertEquals("favorable", ReportService.forecastTone(lower, last, false));
        assertEquals(null, ReportService.forecastTone(last, last, true));
        assertEquals(null, ReportService.forecastTone(null, last, true));
    }

    @Test
    void targetNamesTheReportAlreadyComparesStayMatched() {
        List<LineCategory> categories = List.of(
                new LineCategory("revenue", "Revenue", (short) 1, true),
                new LineCategory("employee_costs", "Employee costs", (short) 4, true),
                new LineCategory("electricity", "Electricity", (short) 6, false),
                new LineCategory("cost_of_sales", "Cost of sales", (short) 3, false),
                new LineCategory("transport_fuel", "Transport and fuel", (short) 8, false));

        assertEquals("revenue", ReportService.categoryForTarget("Revenue", categories));
        assertEquals("revenue", ReportService.categoryForTarget("revenue", categories));
        assertEquals("electricity", ReportService.categoryForTarget("Electricity", categories));
        assertEquals("electricity", ReportService.categoryForTarget("Electricity cost", categories));
        assertEquals("employee_costs", ReportService.categoryForTarget("Employee costs", categories));
        assertEquals("cost_of_sales", ReportService.categoryForTarget("Cost of sales", categories));
        assertEquals("transport_fuel", ReportService.categoryForTarget("Transport and fuel", categories));
        assertEquals(null, ReportService.categoryForTarget("Gross profit margin", categories));
        assertEquals(null, ReportService.categoryForTarget("  gross profit margin ", categories));
    }

    private static BudgetSubmission submission(String id, String status, Instant submittedAt) {
        return new BudgetSubmission(id, "unit", "user",
                LocalDate.parse("2021-03-01"), LocalDate.parse("2026-02-28"),
                status, submittedAt, null, "manual", null, 1);
    }

    private static FinancialLineItem line(String submissionId, String category, String period, String amount) {
        return new FinancialLineItem(submissionId + category + period, submissionId, category,
                LocalDate.parse(period), new BigDecimal(amount), FinancialLineItem.ANNUAL);
    }
}
