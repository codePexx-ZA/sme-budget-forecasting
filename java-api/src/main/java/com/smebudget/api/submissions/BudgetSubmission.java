package com.smebudget.api.submissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Maps one row of budget_submission. */
@Entity
@Table(name = "budget_submission")
public class BudgetSubmission {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_SUPERSEDED = "superseded";

    public static final String IMPORT_MANUAL = "manual";
    public static final String IMPORT_TEMPLATE = "template";
    public static final String IMPORT_MAPPED = "mapped_import";

    public static final Set<String> IMPORT_METHODS = Set.of(IMPORT_MANUAL, IMPORT_TEMPLATE, IMPORT_MAPPED);
    public static final Set<Integer> SOURCE_SCALES = Set.of(1, 1000, 1000000);
    /** A submitter may still change these. Approved history is never edited or deleted. */
    public static final Set<String> EDITABLE_STATUSES = Set.of(STATUS_DRAFT, STATUS_REJECTED);

    @Id
    @Column(name = "submission_id", length = 32)
    private String submissionId;

    @Column(name = "unit_id", nullable = false, length = 32)
    private String unitId;

    @Column(name = "submitted_by", nullable = false, length = 32)
    private String submittedBy;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "replaces_submission_id", length = 32)
    private String replacesSubmissionId;

    @Column(name = "import_method", nullable = false, length = 20)
    private String importMethod;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_scale", nullable = false)
    private int sourceScale;

    /** Required by JPA. */
    protected BudgetSubmission() {
    }

    public BudgetSubmission(String submissionId, String unitId, String submittedBy,
                            LocalDate periodStart, LocalDate periodEnd, String status, Instant submittedAt,
                            String replacesSubmissionId, String importMethod, String sourceFileName, int sourceScale) {
        this.submissionId = submissionId;
        this.unitId = unitId;
        this.submittedBy = submittedBy;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.status = status;
        this.submittedAt = submittedAt;
        this.replacesSubmissionId = replacesSubmissionId;
        this.importMethod = importMethod;
        this.sourceFileName = sourceFileName;
        this.sourceScale = sourceScale;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getReplacesSubmissionId() {
        return replacesSubmissionId;
    }

    public void setReplacesSubmissionId(String replacesSubmissionId) {
        this.replacesSubmissionId = replacesSubmissionId;
    }

    public String getImportMethod() {
        return importMethod;
    }

    public void setImportMethod(String importMethod) {
        this.importMethod = importMethod;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public int getSourceScale() {
        return sourceScale;
    }

    public void setSourceScale(int sourceScale) {
        this.sourceScale = sourceScale;
    }
}
