package com.smebudget.api.submissions;

import java.time.Instant;
import java.time.LocalDate;

/** One row for the submissions list. Line amounts are loaded only on the detail call. */
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
        int lineItemCount) {
}
