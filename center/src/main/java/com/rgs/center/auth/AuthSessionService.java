package com.rgs.center.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
class AuthSessionService {

    private static final int TOKEN_BYTES = 32;

    private final JdbcClient jdbcClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration sessionDuration;
    private final Duration activeWindow;

    AuthSessionService(
            JdbcClient jdbcClient,
            @Value("${rgs.auth.session-duration:8h}") Duration sessionDuration,
            @Value("${rgs.auth.active-window:90s}") Duration activeWindow
    ) {
        this.jdbcClient = jdbcClient;
        this.sessionDuration = sessionDuration;
        this.activeWindow = activeWindow;
    }

    @Transactional
    IssuedToken issue(AuthPrincipal principal, String remoteAddress, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionDuration);

        jdbcClient.sql("""
                        INSERT INTO auth_sessions
                            (id, user_id, token_hash, created_at, last_seen_at, expires_at,
                             remote_address, user_agent)
                        VALUES
                            (:id, :userId, :tokenHash, :now, :now, :expiresAt,
                             :remoteAddress, :userAgent)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", principal.id())
                .param("tokenHash", hash(token))
                .param("now", asTimestamp(now))
                .param("expiresAt", asTimestamp(expiresAt))
                .param("remoteAddress", truncate(remoteAddress, 64))
                .param("userAgent", truncate(userAgent, 512))
                .update();

        purgeOldSessions(now);
        return new IssuedToken(token, expiresAt);
    }

    @Transactional
    Optional<AuthenticatedSession> authenticate(String token) {
        if (token == null || token.length() < 32 || token.length() > 128) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Optional<AuthenticatedSession> session = jdbcClient.sql("""
                        SELECT s.id, u.id AS user_id, u.login, u.treename
                        FROM auth_sessions s
                        JOIN appusers u ON u.id = s.user_id
                        WHERE s.token_hash = :tokenHash
                          AND s.revoked_at IS NULL
                          AND s.expires_at > :now
                          AND u.active = TRUE
                        """)
                .param("tokenHash", hash(token))
                .param("now", asTimestamp(now))
                .query((rs, rowNum) -> new AuthenticatedSession(
                        rs.getObject("id", UUID.class),
                        new AuthPrincipal(
                                rs.getLong("user_id"),
                                rs.getString("login"),
                                rs.getString("treename")
                        )
                ))
                .optional();

        session.ifPresent(value -> jdbcClient.sql("""
                        UPDATE auth_sessions
                        SET last_seen_at = :now
                        WHERE id = :id AND last_seen_at < :refreshBefore
                        """)
                .param("now", asTimestamp(now))
                .param("id", value.sessionId())
                .param("refreshBefore", asTimestamp(now.minusSeconds(15)))
                .update());

        return session;
    }

    void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        jdbcClient.sql("""
                        UPDATE auth_sessions
                        SET revoked_at = :now
                        WHERE token_hash = :tokenHash AND revoked_at IS NULL
                        """)
                .param("now", asTimestamp(Instant.now()))
                .param("tokenHash", hash(token))
                .update();
    }

    int countConnected() {
        Instant now = Instant.now();
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auth_sessions s
                        JOIN appusers u ON u.id = s.user_id
                        WHERE s.revoked_at IS NULL
                          AND s.expires_at > :now
                          AND s.last_seen_at >= :activeSince
                          AND u.active = TRUE
                        """)
                .param("now", asTimestamp(now))
                .param("activeSince", asTimestamp(now.minus(activeWindow)))
                .query(Long.class)
                .single();
        return count == null ? 0 : Math.toIntExact(count);
    }

    private void purgeOldSessions(Instant now) {
        jdbcClient.sql("""
                        DELETE FROM auth_sessions
                        WHERE expires_at < :cutoff
                           OR (revoked_at IS NOT NULL AND revoked_at < :cutoff)
                        """)
                .param("cutoff", asTimestamp(now.minus(Duration.ofDays(7))))
                .update();
    }

    private static OffsetDateTime asTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
