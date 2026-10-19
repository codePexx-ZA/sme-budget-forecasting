package com.budgetapp.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

// This class represents a single financial line item and maps to the 'financial_line_item' table
// Each line item stores one amount for one category for one period within a submission

@Entity
@Table(name = "financial_line_item")
public class FinancialLineItem {

    @Id
    @Column(name = "line_item_id", length = 32)
    private String lineItemId;

    @ManyToOne
    @JoinColumn(name = "submission_id", nullable = false)
    private BudgetSubmission submission;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "frequency", nullable = false, length = 20)
    private String frequency = "annual";

    // Getters and setters
    public String getLineItemId() { return lineItemId; }
    public void setLineItemId(String lineItemId) { this.lineItemId = lineItemId; }

    public BudgetSubmission getSubmission() { return submission; }
    public void setSubmission(BudgetSubmission submission) { this.submission = submission; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
}