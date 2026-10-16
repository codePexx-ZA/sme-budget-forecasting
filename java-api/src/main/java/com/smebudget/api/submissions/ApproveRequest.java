package com.smebudget.api.submissions;

import jakarta.validation.constraints.Size;

/** Optional comment when an approver accepts a submission. */
public record ApproveRequest(
        @Size(max = 2000, message = "Comments must be 2000 characters or fewer.")
        String comments) {
}
