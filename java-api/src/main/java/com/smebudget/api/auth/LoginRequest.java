package com.smebudget.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/login. The messages are shown to the user as they are. */
public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password is too long.") // BCrypt only uses the first 72 bytes
        String password) {
}
