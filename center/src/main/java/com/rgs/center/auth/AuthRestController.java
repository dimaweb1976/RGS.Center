package com.rgs.center.auth;

import com.rgs.center.api.auth.CsrfResponse;
import com.rgs.center.api.auth.CurrentUserResponse;
import com.rgs.center.api.auth.LoginRequest;
import com.rgs.center.api.auth.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON authentication endpoints for the separate Next.js frontend. */
@RestController
@RequestMapping("/api/v1/auth")
final class AuthRestController {

    private static final Logger log = LoggerFactory.getLogger(
            AuthRestController.class
    );
    /** Default cookie name configured by {@link SecurityConfiguration}. */
    private static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";

    private final AuthenticationService authenticationService;
    private final AuthSessionService sessionService;
    private final AuthCookieService cookieService;

    AuthRestController(
            AuthenticationService authenticationService,
            AuthSessionService sessionService,
            AuthCookieService cookieService
    ) {
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    /**
     * Forces Spring Security to create the readable CSRF cookie and returns
     * stable transport metadata without duplicating the raw token in JSON.
     */
    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName()
        );
        if (token == null) {
            throw new IllegalStateException("CSRF token is unavailable");
        }
        // Accessing the deferred token causes CookieCsrfTokenRepository to save it.
        token.getToken();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(
                        XSRF_COOKIE_NAME,
                        token.getHeaderName()
                ));
    }

    /** Verifies JSON credentials and writes the existing HttpOnly token cookie. */
    @PostMapping("/login")
    ResponseEntity<?> login(
            @RequestBody LoginRequest credentials,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            cookieService.read(request).ifPresent(sessionService::revoke);
            IssuedToken token = authenticationService.login(
                    credentials.login(),
                    credentials.password(),
                    clientAddress(request),
                    request.getHeader("User-Agent")
            );
            cookieService.write(response, token);
            log.info("User '{}' authenticated through REST API", credentials.login());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new LoginResponse(true, token.expiresAt()));
        } catch (AuthenticationException exception) {
            // Do not write supplied credentials or untrusted login text to logs.
            log.warn("REST authentication failed");
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid login or password"
            );
            problem.setTitle("Authentication failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .cacheControl(CacheControl.noStore())
                    .body(problem);
        }
    }

    /** Returns the principal restored by {@link AuthTokenFilter}. */
    @GetMapping("/me")
    ResponseEntity<CurrentUserResponse> currentUser(
            Authentication authentication
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        int connected = sessionService.countConnected();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CurrentUserResponse(
                        principal.login(),
                        principal.roleName(),
                        connected,
                        connected > 1
                ));
    }

    /** Revokes only the current browser token and expires its cookie. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        cookieService.read(request).ifPresent(sessionService::revoke);
        cookieService.clear(response);
        org.springframework.security.core.context.SecurityContextHolder
                .clearContext();
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
