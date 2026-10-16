package com.smebudget.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smebudget.api.common.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Unit tests for login and logout. No database needed: the user table is mocked. */
class AuthServiceTest {

    private static final String PASSWORD = "Test@1234";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost keeps tests fast
    private AppUserRepository users;
    private SessionStore sessions;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        sessions = new SessionStore(8);
        authService = new AuthService(users, encoder, sessions);
    }

    private AppUser user(String status) {
        return new AppUser("u1", "unit1", "Sam Submitter", "submitter@example.com",
                encoder.encode(PASSWORD), "submitter", status);
    }

    @Test
    void correctPasswordStartsSession() {
        when(users.findByEmailIgnoreCase("submitter@example.com")).thenReturn(Optional.of(user("active")));

        LoginResponse response = authService.login(new LoginRequest(" Submitter@Example.com ", PASSWORD));

        assertNotNull(response.token());
        assertEquals("submitter", response.user().role());
        assertTrue(sessions.find(response.token()).isPresent());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmailIgnoreCase("submitter@example.com")).thenReturn(Optional.of(user("active")));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("submitter@example.com", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(AuthService.BAD_LOGIN_MESSAGE, ex.getMessage());
    }

    @Test
    void unknownEmailGetsSameMessageAsWrongPassword() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(AuthService.BAD_LOGIN_MESSAGE, ex.getMessage());
    }

    @Test
    void inactiveUserCannotLogIn() {
        when(users.findByEmailIgnoreCase("submitter@example.com")).thenReturn(Optional.of(user("inactive")));

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("submitter@example.com", PASSWORD)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void logoutEndsSession() {
        when(users.findByEmailIgnoreCase("submitter@example.com")).thenReturn(Optional.of(user("active")));
        LoginResponse response = authService.login(new LoginRequest("submitter@example.com", PASSWORD));

        authService.logout(new AuthenticatedUser("u1", "submitter", response.token()));

        assertTrue(sessions.find(response.token()).isEmpty());
    }

    @Test
    void expiredSessionIsNotFound() {
        SessionStore expiring = new SessionStore(-1); // sessions that expired an hour ago
        String token = expiring.create("u1", "submitter");

        assertTrue(expiring.find(token).isEmpty());
    }
}
