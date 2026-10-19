package com.smebudget.api.config;

import com.smebudget.api.auth.SessionStore;
import com.smebudget.api.auth.TokenAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Shared security rules for the whole API (owned by Dean; ask before changing).
 *
 * Only POST /api/auth/login and POST /api/auth/register are public. Every other endpoint needs the header
 * "Authorization: Bearer &lt;token&gt;" from a successful login. Roles become authorities
 * ROLE_SUBMITTER, ROLE_APPROVER and ROLE_DECISION_MAKER, so a feature can restrict an
 * endpoint with, for example, {@code @PreAuthorize("hasRole('APPROVER')")}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionStore sessionStore) throws Exception {
        http
                // The Android app sends a token header, not browser cookies, so CSRF protection is not needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable()) // logout is our own endpoint: POST /api/auth/logout
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to continue."))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Your role does not have access to this.")))
                .addFilterBefore(new TokenAuthFilter(sessionStore), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt matches the $2a$ hashes that database/init/03_seed.sql stores in app_user.password_hash. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
