package com.smebudget.app.targets;

import java.util.List;

/** Meeting report from GET /api/reports/meeting. Amounts are already formatted. */
public class MeetingReportDto {

    public String businessName;
    public String unitName;
    public String preparedFor;
    public String preparedAt;
    public String historyNote;
    public String forecastNote;
    public String excludedNote;
    public List<ApprovalNote> approvals;
    public List<ComparisonRow> rows;
    public List<Fact> results;
    public List<TargetLine> targets;
    public List<WarningLine> warnings;
    public String shareText;

    public static class ComparisonRow {
        public String category;
        public String lastYear;
        public String movingAverage;
        public String exponentialSmoothing;
    }

    public static class ApprovalNote {
        public String approver;
        public String when;
        public String period;
        public String comment;
    }

    public static class Fact {
        public String label;
        public String value;
        public String note;
    }

    public static class TargetLine {
        public String name;
        public String value;
        public String period;
        public String lastYear;
        public String movingAverage;
        public String exponentialSmoothing;
    }

    public static class WarningLine {
        public String severity;
        public String message;
    }
}
