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

@RestController
@RequestMapping("/api/onlyOne")
class RestApiOnlyOneController {

    static final String LOCK_TOKEN_HEADER = "X-Lock-Token";

    private final OnlyOneLockService lockService;

    RestApiOnlyOneController(OnlyOneLockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping("/enter")
    ResponseEntity<?> enter(
            @RequestBody EnterRequest request,
            HttpServletRequest httpRequest
    ) {
        if (request == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }

        EnterResult result = lockService.enter(
                request.password(),
                resolveClientIp(httpRequest)
        );

        if (result == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid password or system busy"));
        }

        return ResponseEntity.ok(new EnterResponse(
                result.token(),
                result.expiresAt()
        ));
    }

    @PostMapping("/updateBusy")
    ResponseEntity<?> updateBusy(
            @RequestHeader(name = LOCK_TOKEN_HEADER, required = false) String token
    ) {
        if (isMissing(token)) {
            return missingTokenResponse();
        }

        LocalDateTime expiresAt = lockService.updateBusy(token);
        if (expiresAt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired lock token"));
        }

        return ResponseEntity.ok(new UpdateBusyResponse("extended", expiresAt));
    }

    @PostMapping("/exit")
    ResponseEntity<?> exit(
            @RequestHeader(name = LOCK_TOKEN_HEADER, required = false) String token
    ) {
        if (isMissing(token)) {
            return missingTokenResponse();
        }
        lockService.exit(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/checkBusy")
    BusyResponse checkBusy() {
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

        LocalDateTime expiresAt = lockService.checkMe(token);
        if (expiresAt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("active", false));
        }

        return ResponseEntity.ok(new CheckMeResponse(true, expiresAt));
    }

    private static ResponseEntity<Map<String, String>> missingTokenResponse() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", LOCK_TOKEN_HEADER + " header is required"));
    }

    private static boolean isMissing(String token) {
        return token == null || token.isBlank();
    }

    private static String resolveClientIp(HttpServletRequest request) {
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

record EnterRequest(String password) {}
record EnterResponse(String token, LocalDateTime expiresAt) {}
record UpdateBusyResponse(String status, LocalDateTime expiresAt) {}
record BusyResponse(boolean busy, LocalDateTime busyUntil, String clientIp) {}
record CheckMeResponse(boolean active, LocalDateTime expiresAt) {}