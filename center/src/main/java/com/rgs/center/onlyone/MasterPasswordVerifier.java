package com.rgs.center.onlyone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class MasterPasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String masterPasswordHash;

    MasterPasswordVerifier(
            PasswordEncoder passwordEncoder,
            @Value("${RGS_MASTER_PASSWORD_BCRYPT:$2a$10$DefaultHashPlaceholderToPreventStartupErrors}") String masterPasswordHash
    ) {
        this.passwordEncoder = passwordEncoder;
        this.masterPasswordHash = masterPasswordHash;
    }

    boolean matches(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (masterPasswordHash == null || masterPasswordHash.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, masterPasswordHash);
    }
}