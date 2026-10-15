package com.budgetapp.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "budget_submission")
public class BudgetSubmission {

    @Id
    @Column(name = "submission_id", length = 32)
    private String submissionId;

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private BusinessUnit unit;

    @ManyToOne
    @JoinColumn(name = "submitted_by", nullable = false)
    private AppUser submittedBy;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "import_method", nullable = false, length = 20)
    private String importMethod = "manual";

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_scale", nullable = false)
    private Integer sourceScale = 1;

    // Getters and setters
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public BusinessUnit getUnit() { return unit; }
    public void setUnit(BusinessUnit unit) { this.unit = unit; }

    public AppUser getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(AppUser submittedBy) { this.submittedBy = submittedBy; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getImportMethod() { return importMethod; }
    public void setImportMethod(String importMethod) { this.importMethod = importMethod; }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    public Integer getSourceScale() { return sourceScale; }
    public void setSourceScale(Integer sourceScale) { this.sourceScale = sourceScale; }
}