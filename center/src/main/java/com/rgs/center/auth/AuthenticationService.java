package com.rgs.center.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AuthenticationService {

    // A valid BCrypt hash used to keep failed-login timing similar for missing users.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5FfK9Qh1g0FWoMhiD9p5W3XlY.W2JgK";

    private final AuthUserRepository userRepository;
    private final AuthSessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    AuthenticationService(
            AuthUserRepository userRepository,
            AuthSessionService sessionService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    IssuedToken login(
            String suppliedLogin,
            String suppliedPassword,
            String remoteAddress,
            String userAgent
    ) {
        String login = suppliedLogin == null ? "" : suppliedLogin.trim();
        String password = suppliedPassword == null ? "" : suppliedPassword;
        if (login.isEmpty() || login.length() > 256 || password.isEmpty()
                || password.length() > 1024) {
            throw new BadCredentialsException("Invalid credentials");
        }

        AuthUser user = userRepository.findByLogin(login).orElse(null);
        String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        if (!passwordEncoder.matches(password, passwordHash) || user == null) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.active()) {
            throw new DisabledException("User is disabled");
        }

        return sessionService.issue(
                new AuthPrincipal(user.id(), user.login(), user.displayName()),
                remoteAddress,
                userAgent
        );
    }
}
