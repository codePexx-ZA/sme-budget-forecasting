package com.smebudget.api.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Holds the comment an approver must send when rejecting a budget. */
public record RejectRequest(
        @NotBlank(message = "A rejection must include a comment.")
        @Size(max = 2000, message = "Comments must be 2000 characters or fewer.")
        String comments) {
}
