package com.rgs.center.onlyone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
class OnlyOneLockService {

    private final OnlyOneLockRepository lockRepository;
    private final MasterPasswordVerifier masterPasswordVerifier;
    private final int ttlMinutes;

    OnlyOneLockService(
            OnlyOneLockRepository lockRepository,
            MasterPasswordVerifier masterPasswordVerifier,
            @Value("${TTL_MINUTES:5}") int ttlMinutes
    ) {
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("TTL_MINUTES must be greater than zero");
        }
        this.lockRepository = lockRepository;
        this.masterPasswordVerifier = masterPasswordVerifier;
        this.ttlMinutes = ttlMinutes;
    }

    EnterResult enter(String password, String clientIp) {
        if (!masterPasswordVerifier.matches(password)) {
            return null; // Неверный пароль
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = lockRepository.tryEnter(token, clientIp, ttlMinutes);
        
        if (expiresAt == null) {
            return null; // Комплекс занят другим пользователем
        }

        return new EnterResult(token, expiresAt);
    }

    LocalDateTime updateBusy(String currentToken) {
        if (currentToken == null || currentToken.isBlank()) {
            return null;
        }
        return lockRepository.tryExtend(currentToken, ttlMinutes);
    }

    void exit(String currentToken) {
        if (currentToken != null && !currentToken.isBlank()) {
            lockRepository.exit(currentToken);
        }
    }

    OnlyOneLockState checkBusy() {
        OnlyOneLockState state = lockRepository.readState();
        if (state == null || !state.busy()) {
            return new OnlyOneLockState(null, null, null, false);
        }
        return state;
    }

    LocalDateTime checkMe(String currentToken) {
        if (currentToken == null || currentToken.isBlank()) {
            return null;
        }
        return lockRepository.findActiveExpiry(currentToken);
    }
}

record EnterResult(String token, LocalDateTime expiresAt) {
}