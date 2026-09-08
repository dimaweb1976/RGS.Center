package com.rgs.center.onlyone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates password verification and the lifecycle of the singleton lease.
 *
 * <p>The service generates opaque tokens, delegates all race-sensitive checks
 * to atomic repository operations, and never uses the client clock to decide
 * whether a lease is active. The TTL is a sliding interval: every valid
 * {@code updateBusy} heartbeat sets a new expiry relative to the database's
 * current time.</p>
 */
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
        // Verify the acquisition credential before generating a candidate
        // token or touching the singleton database row.
        if (!masterPasswordVerifier.matches(password)) {
            throw new InvalidMasterPasswordException();
        }

        // UUID is an opaque bearer token. Possession of it proves lease
        // ownership until exit or expiry, so clients must not log or share it.
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = lockRepository
                .tryEnter(token, clientIp, ttlMinutes)
                .orElseThrow(SystemBusyException::new);
        return new EnterResult(token, expiresAt);
    }

    Optional<LocalDateTime> updateBusy(String currentToken) {
        // Empty means that the token is unknown or its lease has already
        // expired; both cases require the client to return to the entry flow.
        return lockRepository.tryExtend(currentToken, ttlMinutes);
    }

    void exit(String currentToken) {
        // Repository token matching makes repeated exit requests harmless.
        lockRepository.exit(currentToken);
    }

    OnlyOneLockState checkBusy() {
        OnlyOneLockState state = lockRepository.readState();
        if (!state.busy()) {
            // Do not expose stale token, IP, or expiry data from an expired row.
            return new OnlyOneLockState(null, null, null, false);
        }
        return state;
    }

    Optional<LocalDateTime> checkMe(String currentToken) {
        // This is also used by the interceptor to authorize protected writes.
        return lockRepository.findActiveExpiry(currentToken);
    }
}

/** Successful acquisition result passed from the service to the controller. */
record EnterResult(String token, LocalDateTime expiresAt) {
}

/** Raised when the acquisition credential does not match the BCrypt hash. */
final class InvalidMasterPasswordException extends RuntimeException {
}

/** Raised when another token owns a lease that has not yet expired. */
final class SystemBusyException extends RuntimeException {
}
