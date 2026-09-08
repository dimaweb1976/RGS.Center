package com.rgs.center.onlyone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Verifies the password that permits a client to acquire the exclusive lease.
 *
 * <p>The browser sends the plain-text master password only to
 * {@code POST /api/onlyOne/enter}. The application compares it with a BCrypt
 * hash and never stores the plain-text value. The hash is read directly from
 * the {@value #ENVIRONMENT_VARIABLE} process environment variable so that it
 * is not committed to {@code application.properties} or returned by an API.</p>
 *
 * <p>This password is only an acquisition credential. After a successful
 * acquisition, all ownership checks use the generated lock token instead.</p>
 */
@Component
class MasterPasswordVerifier {

    static final String ENVIRONMENT_VARIABLE = "RGS_MASTER_PASSWORD_BCRYPT";

    private final PasswordEncoder passwordEncoder;
    private final String masterPasswordHash;

    @Autowired
    MasterPasswordVerifier(PasswordEncoder passwordEncoder) {
        // NetBeans, a service wrapper, or start-rgs.cmd must provide the
        // environment variable to the Java process before Spring starts.
        this(passwordEncoder, System.getenv(ENVIRONMENT_VARIABLE));
    }

    MasterPasswordVerifier(PasswordEncoder passwordEncoder, String masterPasswordHash) {
        this.passwordEncoder = passwordEncoder;
        this.masterPasswordHash = masterPasswordHash;
    }

    boolean matches(String rawPassword) {
        // Fail explicitly instead of silently rejecting every password. This
        // makes a missing deployment secret distinguishable from bad input.
        if (masterPasswordHash == null || masterPasswordHash.isBlank()) {
            throw new IllegalStateException(
                    ENVIRONMENT_VARIABLE + " is not configured"
            );
        }
        // PasswordEncoder.matches performs the BCrypt comparison. The stored
        // hash cannot be used as the request password.
        return rawPassword != null
                && !rawPassword.isEmpty()
                && passwordEncoder.matches(rawPassword, masterPasswordHash);
    }
}
