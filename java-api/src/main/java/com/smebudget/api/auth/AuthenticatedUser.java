package com.smebudget.api.auth;

/**
 * The logged-in user attached to each request. Any controller can receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}, e.g. to save user.userId() as submitted_by.
 */
public record AuthenticatedUser(String userId, String role, String token) {
}
