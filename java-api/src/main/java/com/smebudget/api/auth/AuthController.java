package com.smebudget.api.auth;

import com.smebudget.api.common.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login endpoints (Dean).
 *
 * POST /api/auth/login   {email, password} -> {token, expiresAt, user}   (public)
 * POST /api/auth/logout  ends the current session                     (needs token)
 * GET  /api/auth/me      the logged-in user's details                  (needs token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user);
        return new MessageResponse("You have been logged out.");
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.currentUser(user);
    }
}
