package com.budgetapp.entities;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "business")
public class Business {

    @Id
    @Column(name = "business_id", length = 32)
    private String businessId;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "country", nullable = false, length = 3)
    private String country = "ZAF";

    @Column(name = "year_end_month", nullable = false)
    private Short yearEndMonth = 2;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Getters and setters
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Short getYearEndMonth() { return yearEndMonth; }
    public void setYearEndMonth(Short yearEndMonth) { this.yearEndMonth = yearEndMonth; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}