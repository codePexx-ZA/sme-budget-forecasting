package com.smebudget.api.submissions;

import java.time.Instant;

/** One recorded approve or reject decision, oldest first when listed on a submission. */
public record ApprovalResponse(
        String approvalId,
        String actedBy,
        String actorName,
        String decision,
        String comments,
        Instant actedAt) {
}
