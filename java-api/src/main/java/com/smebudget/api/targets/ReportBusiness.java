package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only row of business. The report uses the legal name. This code does not create the table. */
@Entity
@Table(name = "business")
public class ReportBusiness {

    @Id
    @Column(name = "business_id", length = 32)
    private String businessId;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    /** Last month of the financial year. 2 is February, the usual South African year-end. */
    @Column(name = "year_end_month", nullable = false)
    private short yearEndMonth = 2;

    protected ReportBusiness() {
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getLegalName() {
        return legalName;
    }

    public int getYearEndMonth() {
        return yearEndMonth;
    }
}
