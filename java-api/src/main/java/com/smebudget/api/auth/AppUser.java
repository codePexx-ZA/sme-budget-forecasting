package com.smebudget.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A row of the app_user table (created by database/init/01_schema.sql, never by this code).
 * role is one of submitter, approver, decision_maker; activeStatus is active or inactive.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    public static final String STATUS_ACTIVE = "active";

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "unit_id")
    private String unitId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role")
    private String role;

    @Column(name = "active_status")
    private String activeStatus;

    /** Required by JPA. */
    protected AppUser() {
    }

    public AppUser(String userId, String unitId, String fullName, String email,
                   String passwordHash, String role, String activeStatus) {
        this.userId = userId;
        this.unitId = unitId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.activeStatus = activeStatus;
    }

    public String getUserId() {
        return userId;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(activeStatus);
    }
}
