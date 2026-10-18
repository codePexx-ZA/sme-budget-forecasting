package com.smebudget.api.submissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * One amount for one income-statement category in one period (financial_line_item).
 * Amounts are rands. frequency is monthly, quarterly or annual.
 */
@Entity
@Table(name = "financial_line_item")
public class FinancialLineItem {

    public static final String MONTHLY = "monthly";
    public static final String QUARTERLY = "quarterly";
    public static final String ANNUAL = "annual";
    public static final Set<String> FREQUENCIES = Set.of(MONTHLY, QUARTERLY, ANNUAL);

    @Id
    @Column(name = "line_item_id", length = 32)
    private String lineItemId;

    @Column(name = "submission_id", nullable = false, length = 32)
    private String submissionId;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "frequency", nullable = false, length = 20)
    private String frequency;

    /** Required by JPA. */
    protected FinancialLineItem() {
    }

    public FinancialLineItem(String lineItemId, String submissionId, String category,
                              LocalDate periodStart, BigDecimal amount, String frequency) {
        this.lineItemId = lineItemId;
        this.submissionId = submissionId;
        this.category = category;
        this.periodStart = periodStart;
        this.amount = amount;
        this.frequency = frequency;
    }

    public String getLineItemId() {
        return lineItemId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }
}
