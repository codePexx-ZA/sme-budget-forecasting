package com.smebudget.api.submissions;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row for the submissions list. Line amounts are loaded only on the detail call.
 * {@code latestRejectionComment} is the newest rejection note, and only while the row is rejected.
 */
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
