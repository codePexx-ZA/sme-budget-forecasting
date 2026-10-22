package com.smebudget.app.submissions;

import java.util.List;

/** Holds the body sent when creating or updating a budget. */
public class SubmissionBody {

    final String periodStart;
    final String periodEnd;
    final String importMethod;
    final String sourceFileName;
    final Integer sourceScale;
    final String replacesSubmissionId;
    final List<LineItemBody> lineItems;

    public SubmissionBody(String periodStart, String periodEnd, String importMethod, String sourceFileName,
                          Integer sourceScale, String replacesSubmissionId, List<LineItemBody> lineItems) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.importMethod = importMethod;
        this.sourceFileName = sourceFileName;
        this.sourceScale = sourceScale;
        this.replacesSubmissionId = replacesSubmissionId;
        this.lineItems = lineItems;
    }
}
