package com.smebudget.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before every request. If the request carries "Authorization: Bearer &lt;token&gt;" for a valid
 * session, it marks the request as logged in as that user, with authority ROLE_&lt;ROLE&gt;.
 * Requests without a valid token stay anonymous, and SecurityConfig rejects them with 401.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionStore sessionStore;

    public TokenAuthFilter(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            sessionStore.find(token).ifPresent(session -> {
                AuthenticatedUser user = new AuthenticatedUser(session.userId(), session.role(), token);
                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority("ROLE_" + session.role().toUpperCase(Locale.ROOT));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
