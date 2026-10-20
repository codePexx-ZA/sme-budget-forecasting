package com.smebudget.api.targets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A rule_alert row written by the C# service. This API only reads open warnings. */
@Entity
@Table(name = "rule_alert")
public class SavedAlert {

    @Id
    @Column(name = "alert_id", length = 32)
    private String alertId;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "resolution_status", nullable = false, length = 50)
    private String resolutionStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedAlert() {
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getResolutionStatus() {
        return resolutionStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
