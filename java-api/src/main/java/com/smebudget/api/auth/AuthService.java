package com.smebudget.api.auth;

import com.smebudget.api.common.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Sign-up, login and logout logic. Passwords are only ever stored and compared as BCrypt hashes. */
@Service
public class AuthService {

    /** One message for "no such email" and "wrong password", so nobody can find out which emails exist. */
    static final String BAD_LOGIN_MESSAGE = "Incorrect email or password.";
    static final String INACTIVE_MESSAGE = "This account is inactive. Please contact your administrator.";
    static final String EMAIL_TAKEN_MESSAGE = "An account with this email already exists. Please log in instead.";

    /** Everyone who signs up is a submitter. Approver and decision maker accounts are set up by the business. */
    static final String NEW_ACCOUNT_ROLE = "submitter";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    /** Business unit new accounts join (the prototype has one: the sample business's "Whole company" unit). */
    private final String newAccountUnitId;
    /** Checked when the email is unknown, so a missing account takes as long to answer as a wrong password. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, SessionStore sessionStore,
                       @Value("${app.auth.new-account-unit-id}") String newAccountUnitId) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessionStore = sessionStore;
        this.newAccountUnitId = newAccountUnitId;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Creates a submitter account and logs the new user in straight away.
     * The role is fixed here, so nobody can give themselves approver rights by signing up.
     */
    public LoginResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN_MESSAGE);
        }

        AppUser user = new AppUser(
                UUID.randomUUID().toString().replace("-", ""), // same 32-character id shape as the database
                newAccountUnitId,
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                NEW_ACCOUNT_ROLE,
                AppUser.STATUS_ACTIVE);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Someone registered the same email at the same moment (the email column is UNIQUE).
            if (users.existsByEmailIgnoreCase(email)) {
                throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN_MESSAGE);
            }
            throw e; // a different problem, e.g. app.auth.new-account-unit-id is not a real business unit
        }
        return startSession(user);
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

        return startSession(user);
    }

    private LoginResponse startSession(AppUser user) {
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
