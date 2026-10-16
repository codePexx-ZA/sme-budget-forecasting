package com.smebudget.app.submissions;

/** One row from GET /api/submissions. Dates are yyyy-MM-dd. */
public class SubmissionSummary {

    public String submissionId;
    public String submittedBy;
    public String submitterName;
    public String periodStart;
    public String periodEnd;
    public String status;
    public String submittedAt;
    public String importMethod;
    public Integer sourceScale;
    public int lineItemCount;
}
