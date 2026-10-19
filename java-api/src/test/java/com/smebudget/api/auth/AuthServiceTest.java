package com.smebudget.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smebudget.api.common.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Unit tests for sign-up, login and logout. No database needed: the user table is mocked. */
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
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authService = new AuthService(users, encoder, sessions, "unit1");
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

    @Test
    void registerCreatesSubmitterAndLogsIn() {
        LoginResponse response = authService.register(
                new RegisterRequest(" New Person ", "New@Example.com", "Secret123"));

        assertEquals("submitter", response.user().role());
        assertEquals("new@example.com", response.user().email());
        assertEquals("New Person", response.user().fullName());
        assertEquals("unit1", response.user().unitId());
        assertEquals(32, response.user().userId().length());
        assertTrue(sessions.find(response.token()).isPresent());
    }

    @Test
    void registerStoresHashNotPassword() {
        authService.register(new RegisterRequest("New Person", "new@example.com", "Secret123"));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().getPasswordHash().startsWith("$2a$"));
        assertTrue(encoder.matches("Secret123", saved.getValue().getPasswordHash()));
    }

    @Test
    void registerRejectsEmailThatExists() {
        when(users.existsByEmailIgnoreCase("submitter@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(
                new RegisterRequest("Someone", "Submitter@example.com", "Secret123")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(users, never()).saveAndFlush(any(AppUser.class));
    }
}
