package com.smebudget.app.submissions;

/** One income-statement category from GET /api/submissions/categories. */
public class CategoryDto {

    public String categoryCode;
    public String displayName;
    public int sortOrder;
    public boolean teamManaged;
}
