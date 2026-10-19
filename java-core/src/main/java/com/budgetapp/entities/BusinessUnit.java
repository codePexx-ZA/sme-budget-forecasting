package com.budgetapp.entities;

import jakarta.persistence.*;

// This class represents a business unit and maps to the 'business_unit' table in the database
// A business unit belongs to a business and can be a company, store, branch or department.

@Entity
@Table(name = "business_unit")
public class BusinessUnit {

    @Id
    @Column(name = "unit_id", length = 32)
    private String unitId;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "unit_name", nullable = false, length = 200)
    private String unitName;

    @Column(name = "unit_type", nullable = false, length = 20)
    private String unitType = "company";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "ZAR";

    // Getters and setters
    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public Business getBusiness() { return business; }
    public void setBusiness(Business business) { this.business = business; }

    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}