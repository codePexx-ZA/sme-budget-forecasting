package com.smebudget.api.submissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One approve or reject decision (approval_action). A rejection must have comments;
 * the database rejects a row that breaks that rule.
 */
@Entity
@Table(name = "approval_action")
public class ApprovalAction {

    public static final String DECISION_APPROVED = "approved";
    public static final String DECISION_REJECTED = "rejected";

    @Id
    @Column(name = "approval_id", length = 32)
    private String approvalId;

    @Column(name = "submission_id", nullable = false, length = 32)
    private String submissionId;

    @Column(name = "acted_by", nullable = false, length = 32)
    private String actedBy;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "comments", length = 2000)
    private String comments;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    /** Required by JPA. */
    protected ApprovalAction() {
    }

    public ApprovalAction(String approvalId, String submissionId, String actedBy,
                          String decision, String comments, Instant actedAt) {
        this.approvalId = approvalId;
        this.submissionId = submissionId;
        this.actedBy = actedBy;
        this.decision = decision;
        this.comments = comments;
        this.actedAt = actedAt;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getActedBy() {
        return actedBy;
    }

    public String getDecision() {
        return decision;
    }

    public String getComments() {
        return comments;
    }

    public Instant getActedAt() {
        return actedAt;
    }
}
