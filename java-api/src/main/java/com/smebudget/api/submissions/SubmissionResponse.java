package com.smebudget.api.submissions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A submission with its lines, approval history and per-period subtotals. */
public record SubmissionResponse(
        String submissionId,
        String unitId,
        String submittedBy,
        String submitterName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        Instant submittedAt,
        String replacesSubmissionId,
        String replacesLabel,
        String importMethod,
        String sourceFileName,
        int sourceScale,
        List<LineItemResponse> lineItems,
        List<ApprovalResponse> approvals,
        List<PeriodTotals> totals) {
}
