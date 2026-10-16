package com.smebudget.app.auth;

/** Body sent to POST /api/auth/login. */
public class LoginRequest {

    final String email;
    final String password;

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
