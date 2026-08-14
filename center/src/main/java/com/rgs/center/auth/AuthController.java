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
            model.addAttribute("error", "Неверный логин или пароль либо пользователь отключён");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return "login";
        }
    }

    @PostMapping("/auth/logout")
    String logout(HttpServletRequest request, HttpServletResponse response) {
        cookieService.read(request).ifPresent(sessionService::revoke);
        cookieService.clear(response);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return "redirect:/login";
    }

    @GetMapping("/auth/connections")
    @ResponseBody
    Map<String, Object> connectionStatus(Authentication authentication) {
        int connected = sessionService.countConnected();
        return Map.of(
                "username", authentication.getName(),
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
