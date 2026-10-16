package com.smebudget.api.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A rejection must say why. The database also requires comments when decision is rejected. */
public record RejectRequest(
        @NotBlank(message = "A rejection must include a comment.")
        @Size(max = 2000, message = "Comments must be 2000 characters or fewer.")
        String comments) {
}
