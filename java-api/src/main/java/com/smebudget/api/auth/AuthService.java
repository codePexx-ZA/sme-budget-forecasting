package com.smebudget.api.auth;

import com.smebudget.api.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Login and logout logic. Passwords are only ever compared as BCrypt hashes. */
@Service
public class AuthService {

    /** One message for "no such email" and "wrong password", so nobody can find out which emails exist. */
    static final String BAD_LOGIN_MESSAGE = "Incorrect email or password.";
    static final String INACTIVE_MESSAGE = "This account is inactive. Please contact your administrator.";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    /** Checked when the email is unknown, so a missing account takes as long to answer as a wrong password. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, SessionStore sessionStore) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessionStore = sessionStore;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Checks the email and password, then starts a session. */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN_MESSAGE);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN_MESSAGE);
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, INACTIVE_MESSAGE);
        }

        String token = sessionStore.create(user.getUserId(), user.getRole());
        SessionStore.Session session = sessionStore.find(token).orElseThrow();
        return new LoginResponse(token, session.expiresAt(), UserResponse.from(user));
    }

    /** Ends the session so its token stops working. */
    public void logout(AuthenticatedUser user) {
        sessionStore.revoke(user.token());
    }

    /** Fresh details of the logged-in user (the app calls this to check its saved session is still valid). */
    public UserResponse currentUser(AuthenticatedUser user) {
        AppUser appUser = users.findById(user.userId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> {
                    sessionStore.revoke(user.token());
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
                });
        return UserResponse.from(appUser);
    }
}
