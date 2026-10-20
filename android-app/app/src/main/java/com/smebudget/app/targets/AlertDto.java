package com.smebudget.app.targets;

/** One economic warning from GET /api/insights/alerts. */
public class AlertDto {

    public String severity;
    public String message;
    public String resolutionStatus;
}
