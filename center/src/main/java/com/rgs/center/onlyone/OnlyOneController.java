package com.rgs.center.onlyone;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Public REST contract for the exclusive-access lease.
 *
 * <p>The normal client lifecycle is:</p>
 * <ol>
 *   <li>Call {@code checkBusy} before showing the password form.</li>
 *   <li>Call {@code enter} with the master password and save the returned token.</li>
 *   <li>Send the token in {@value #LOCK_TOKEN_HEADER} when checking ownership,
 *       extending the lease, or invoking protected state-changing resources.</li>
 *   <li>Periodically call {@code updateBusy}; otherwise the database lease
 *       expires automatically.</li>
 *   <li>Call {@code exit} when leaving the protected screen.</li>
 * </ol>
 *
 * <p>The controller translates domain outcomes into HTTP responses. Lease
 * ownership and expiration are decided by the service/repository against the
 * database clock, not by values supplied by the browser.</p>
 */
@RestController
@RequestMapping("/api/onlyOne")
class OnlyOneController {

    static final String LOCK_TOKEN_HEADER = "X-Lock-Token";

    private final OnlyOneLockService lockService;

    OnlyOneController(OnlyOneLockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping("/enter")
    ResponseEntity<?> enter(
            @RequestBody EnterRequest request,
            HttpServletRequest httpRequest
    ) {
        // A successful response is the only operation that creates and returns
        // a token. The raw master password is not retained after verification.
        try {
            EnterResult result = lockService.enter(
                    request.password(),
                    resolveClientIp(httpRequest)
            );
            return ResponseEntity.ok(new EnterResponse(
                    result.token(),
                    result.expiresAt()
            ));
        } catch (InvalidMasterPasswordException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid password"));
        } catch (SystemBusyException exception) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", "System is busy"));
        }
    }

    @PostMapping("/updateBusy")
    ResponseEntity<?> updateBusy(
            @RequestHeader(name = LOCK_TOKEN_HEADER, required = false) String token
    ) {
        if (isMissing(token)) {
            return missingTokenResponse();
        }
        // Extension is permitted only when this token still owns an active
        // lease. An expired token cannot revive the lease.
        return lockService.updateBusy(token)
                .<ResponseEntity<?>>map(expiresAt -> ResponseEntity.ok(
                        new UpdateBusyResponse("extended", expiresAt)
                ))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired lock token")));
    }

    @PostMapping("/exit")
    ResponseEntity<?> exit(
            @RequestHeader(name = LOCK_TOKEN_HEADER, required = false) String token
    ) {
        if (isMissing(token)) {
            return missingTokenResponse();
        }
        // Exit is idempotent: an unknown or already released token still yields
        // 204 and can never release a lease owned by a different token.
        lockService.exit(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/checkBusy")
    BusyResponse checkBusy() {
        // This endpoint intentionally does not disclose the active token.
        OnlyOneLockState state = lockService.checkBusy();
        return new BusyResponse(
                state.busy(),
                state.expiresAt(),
                state.clientIp()
        );
    }

    @GetMapping("/checkMe")
    ResponseEntity<?> checkMe(
            @RequestHeader(name = LOCK_TOKEN_HEADER, required = false) String token
    ) {
        if (isMissing(token)) {
            return missingTokenResponse();
        }
        // checkMe answers whether the caller, not merely some client, owns the
        // currently active lease.
        Optional<LocalDateTime> expiresAt = lockService.checkMe(token);
        if (expiresAt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("active", false));
        }
        return ResponseEntity.ok(new CheckMeResponse(true, expiresAt.get()));
    }

    private static ResponseEntity<Map<String, String>> missingTokenResponse() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", LOCK_TOKEN_HEADER + " header is required"));
    }

    private static boolean isMissing(String token) {
        return token == null || token.isBlank();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        // Keep only the first proxy address and enforce the VARCHAR(45) limit.
        // Deployment must configure trusted proxies so clients cannot spoof
        // X-Forwarded-For when this diagnostic value is security-sensitive.
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstAddress = forwardedFor.split(",", 2)[0].trim();
            if (!firstAddress.isEmpty() && firstAddress.length() <= 45) {
                return firstAddress;
            }
        }
        return request.getRemoteAddr();
    }
}

/** Password supplied only while attempting to acquire the lease. */
record EnterRequest(String password) {
}

/** Newly generated ownership token and its database-calculated expiry time. */
record EnterResponse(String token, LocalDateTime expiresAt) {
}

/** Confirmation that the active lease was extended. */
record UpdateBusyResponse(String status, LocalDateTime expiresAt) {
}

/** Public lock state; deliberately contains no ownership token. */
record BusyResponse(boolean busy, LocalDateTime busyUntil, String clientIp) {
}

/** Result of checking the current caller's token. */
record CheckMeResponse(boolean active, LocalDateTime expiresAt) {
}
