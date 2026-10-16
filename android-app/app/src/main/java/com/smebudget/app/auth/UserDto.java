package com.smebudget.app.auth;

/** A user as returned by the API. role is submitter, approver or decision_maker. */
public class UserDto {

    public String userId;
    public String fullName;
    public String email;
    public String role;
    public String unitId;
}
