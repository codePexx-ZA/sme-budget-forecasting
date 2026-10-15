package com.smebudget.api.auth;

/** User details sent to the app. Never includes the password hash. */
public record UserResponse(String userId, String fullName, String email, String role, String unitId) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getUserId(), user.getFullName(), user.getEmail(), user.getRole(), user.getUnitId());
    }
}
