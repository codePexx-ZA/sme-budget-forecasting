package com.smebudget.api.submissions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Holds the body for creating or updating a budget. */
public record SubmissionRequest(
        @NotNull(message = "Period start is required.")
        LocalDate periodStart,

        @NotNull(message = "Period end is required.")
        LocalDate periodEnd,

        String importMethod,

        @Size(max = 255, message = "File name must be 255 characters or fewer.")
        String sourceFileName,

        Integer sourceScale,

        String replacesSubmissionId,

        @Valid
        List<LineItemRequest> lineItems) {
}
