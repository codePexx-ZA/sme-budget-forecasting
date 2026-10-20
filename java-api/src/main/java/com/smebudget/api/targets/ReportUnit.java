package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only row of business_unit. Links the logged-in user to the business name. */
@Entity
@Table(name = "business_unit")
public class ReportUnit {

    @Id
    @Column(name = "unit_id", length = 32)
    private String unitId;

    @Column(name = "business_id", nullable = false, length = 32)
    private String businessId;

    @Column(name = "unit_name", nullable = false, length = 200)
    private String unitName;

    protected ReportUnit() {
    }

    public String getUnitId() {
        return unitId;
    }

    public String getBusinessId() {
        return businessId;
    }

    public String getUnitName() {
        return unitName;
    }
}
