package com.smebudget.app.submissions;

import java.util.List;

/** A submission with its lines, decisions and per-period subtotals. */
public class SubmissionDetail {

    public String submissionId;
    public String unitId;
    public String submittedBy;
    public String submitterName;
    public String periodStart;
    public String periodEnd;
    public String status;
    public String submittedAt;
    public String replacesSubmissionId;
    public String replacesLabel;
    public String importMethod;
    public String sourceFileName;
    public Integer sourceScale;
    public List<LineItemDto> lineItems;
    public List<ApprovalDto> approvals;
    public List<PeriodTotalsDto> totals;
}
