package com.budgetapp.entities;

import jakarta.persistence.*;

// This class represents a user in the system and maps to the 'app_user' table in the database
// Users are assigned one of three roles: submitter, approver or decision_maker

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
    private BusinessUnit unit;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "active_status", nullable = false, length = 20)
    private String activeStatus = "active";

    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BusinessUnit getUnit() { return unit; }
    public void setUnit(BusinessUnit unit) { this.unit = unit; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }
}