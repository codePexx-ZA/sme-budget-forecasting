package com.smebudget.api.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps track of logged-in sessions.
 *
 * Each login gets a long random token (256 bits, so it cannot be guessed). The server remembers
 * which user owns it until it expires or the user logs out, which is how logout really ends the
 * session on the server. Sessions are held in memory, so restarting the API logs everyone out;
 * the app then shows the login screen again.
 */
@Component
public class SessionStore {

    /** What the server remembers about one login. */
    public record Session(String userId, String role, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long sessionHours;

    public SessionStore(@Value("${app.auth.session-hours:8}") long sessionHours) {
        this.sessionHours = sessionHours;
    }

    /** Starts a session and returns its new token. */
    public String create(String userId, String role) {
        removeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(userId, role, Instant.now().plus(sessionHours, ChronoUnit.HOURS)));
        return token;
    }

    /** Returns the session for a token, or empty if the token is unknown, logged out or expired. */
    public Optional<Session> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Ends a session (logout). After this the token no longer works. */
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private void removeExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(session -> session.expiresAt().isBefore(now));
    }
}
