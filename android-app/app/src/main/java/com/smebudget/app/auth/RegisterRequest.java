package com.smebudget.app.auth;

/** Body sent to POST /api/auth/register. */
public class RegisterRequest {

    final String fullName;
    final String email;
    final String password;

    public RegisterRequest(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }
}
