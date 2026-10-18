package com.smebudget.app.auth;

/** Reply from POST /api/auth/login. */
public class LoginResponse {

    public String token;
    /** When the session ends, as an ISO date-time, e.g. "2026-10-15T18:00:00Z". */
    public String expiresAt;
    public UserDto user;
}
