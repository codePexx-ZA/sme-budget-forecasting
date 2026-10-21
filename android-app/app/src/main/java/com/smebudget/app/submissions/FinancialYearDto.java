package com.smebudget.app.submissions;

/** Dates from GET /api/reports/financial-year. Dates are yyyy-MM-dd. */
public class FinancialYearDto {

    public int yearEndMonth;
    public String thisStart;
    public String thisEnd;
    public String nextStart;
    public String nextEnd;
}
