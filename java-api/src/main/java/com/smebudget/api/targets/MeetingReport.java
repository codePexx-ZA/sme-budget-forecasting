package com.smebudget.api.targets;

import java.util.List;

/**
 * One meeting report. Figures are already formatted.
 * The screen reads the structured fields. {@code shareText} is the email copy.
 */
public record MeetingReport(
        String businessName,
        String unitName,
        String preparedFor,
        String preparedAt,
        String historyNote,
        String forecastNote,
        String excludedNote,
        List<ApprovalNote> approvals,
        List<ComparisonRow> rows,
        List<Fact> results,
        List<TargetLine> targets,
        List<WarningLine> warnings,
        String shareText
) {

    public record ComparisonRow(
            String category,
            String lastYear,
            String movingAverage,
            String exponentialSmoothing,
            String movingAverageTone,
            String exponentialSmoothingTone
    ) {
    }

    /** Who approved a submission, kept as separate fields so the screen can label each one. */
    public record ApprovalNote(String approver, String when, String period, String comment) {
    }

    /** A labelled figure. {@code note} is optional, for example a target next to a margin. */
    public record Fact(String label, String value, String note) {
    }

    /** A KPI target. Comparison fields are null when the target is not a rand amount for a category. */
    public record TargetLine(
            String name,
            String value,
            String period,
            String lastYear,
            String movingAverage,
            String exponentialSmoothing,
            String movingAverageTone,
            String exponentialSmoothingTone
    ) {
    }

    public record WarningLine(String severity, String message) {
    }
}
