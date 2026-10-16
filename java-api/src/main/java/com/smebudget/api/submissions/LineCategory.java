package com.smebudget.api.submissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A fixed income-statement category from line_category (seeded, not created here). */
@Entity
@Table(name = "line_category")
public class LineCategory {

    @Id
    @Column(name = "category_code", length = 50)
    private String categoryCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "team_managed", nullable = false)
    private boolean teamManaged;

    /** Required by JPA. */
    protected LineCategory() {
    }

    public LineCategory(String categoryCode, String displayName, short sortOrder, boolean teamManaged) {
        this.categoryCode = categoryCode;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
        this.teamManaged = teamManaged;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isTeamManaged() {
        return teamManaged;
    }
}
