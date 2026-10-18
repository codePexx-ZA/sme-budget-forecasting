package com.smebudget.api.auth;

import java.time.Instant;

/** Returned after a successful login. The app sends {@code token} back as "Authorization: Bearer ...". */
public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
}
