package com.rgs.center.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Restores Spring Security authentication from {@code RGS_AUTH_TOKEN} once per
 * request. No state is stored in an HTTP session.
 */
@Component
class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private final AuthCookieService cookieService;
    private final AuthSessionService sessionService;

    AuthTokenFilter(AuthCookieService cookieService, AuthSessionService sessionService) {
        this.cookieService = cookieService;
        this.sessionService = sessionService;
    }

    /**
     * Validates the cookie through {@link AuthSessionService} and installs an
     * {@link AuthPrincipal} plus {@code ROLE_<ROLENAME>} into the current
     * {@link SecurityContextHolder}.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            cookieService.read(request)
                    .flatMap(sessionService::authenticate)
                    .ifPresent(session -> {
                        AuthPrincipal principal = session.principal();
                        // Role names are stored lowercase in PostgreSQL and exposed to
                        // Spring Security using the conventional ROLE_ prefix.
                        UsernamePasswordAuthenticationToken authentication =
                                UsernamePasswordAuthenticationToken.authenticated(
                                        principal,
                                        null,
                                        List.of(new SimpleGrantedAuthority(
                                                "ROLE_" + principal.roleName()
                                                        .toUpperCase(Locale.ROOT)
                                        ))
                                );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (RuntimeException exception) {
            log.warn("Could not authenticate browser token", exception);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
