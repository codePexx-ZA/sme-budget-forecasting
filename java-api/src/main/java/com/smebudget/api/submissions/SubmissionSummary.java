package com.smebudget.api.submissions;

import java.time.Instant;
import java.time.LocalDate;

/** Holds one budget-list row, including the latest rejection comment. */
public record SubmissionSummary(
        String submissionId,
        String submittedBy,
        String submitterName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        Instant submittedAt,
        String importMethod,
        int sourceScale,
        int lineItemCount,
        String latestRejectionComment) {
}
