package com.rgs.center.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/** HTTP endpoints for login, logout and the connected-browser indicator. */
@Controller
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;
    private final AuthSessionService sessionService;
    private final AuthCookieService cookieService;

    AuthController(
            AuthenticationService authenticationService,
            AuthSessionService sessionService,
            AuthCookieService cookieService
    ) {
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @GetMapping("/login")
    String loginPage(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal) {
            return "redirect:/";
        }
        return "login";
    }

    /** Redirects accidental browser GET requests to the actual login page. */
    @GetMapping("/auth/login")
    String loginPageAlias() {
        return "redirect:/login";
    }

    /**
     * Revokes the token already present in this browser, checks credentials,
     * creates a new server-side session and writes its raw token to the cookie.
     */
    @PostMapping("/auth/login")
    String login(
            @RequestParam String login,
            @RequestParam String password,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        try {
            cookieService.read(request).ifPresent(sessionService::revoke);
            IssuedToken token = authenticationService.login(
                    login,
                    password,
                    clientAddress(request),
                    userAgent
            );
            cookieService.write(response, token);
            log.info("User '{}' authenticated", login);
            return "redirect:/";
        } catch (AuthenticationException exception) {
            log.warn("Authentication failed for login '{}'", login);
            model.addAttribute("login", login);
            model.addAttribute("error", "Неверный логин или пароль");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return "login";
        }
    }

    /** Revokes the server-side session and expires the browser cookie. */
    @PostMapping("/auth/logout")
    String logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.read(request).ifPresent(sessionService::revoke);
        cookieService.clear(response);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return "redirect:/login";
    }

    /** Returns the current principal and number of recently active token sessions. */
    @GetMapping("/auth/connections")
    @ResponseBody
    Map<String, Object> connectionStatus(Authentication authentication) {
        int connected = sessionService.countConnected();
        //AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Map.of(
                    "authenticated", false,
                    "username", "anonymous",
                    "role", "ANONYMOUS",
                    "connected", connected,
                    "warning", connected > 1
            );
        }
        
        
        
        return Map.of(
                "username", principal.login(),
                "role", principal.roleName(),
                "connected", connected,
                "warning", connected > 1
        );
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
