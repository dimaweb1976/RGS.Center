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

/**
 * Owns the complete server-side token lifecycle: issuance, validation,
 * activity tracking, revocation and delayed removal of old sessions.
 *
 * <p>The raw token is returned to the browser only. PostgreSQL stores its
 * SHA-256 digest in {@code auth_sessions.token_hash}.</p>
 */
@Service
class AuthSessionService {

    /** 32 random bytes provide 256 bits of token entropy. */
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

    /**
     * Creates a URL-safe random token, stores only its hash and returns the raw
     * value with the absolute expiration time for creation of the browser cookie.
     */
    @Transactional
    IssuedToken issue(AuthPrincipal principal, String remoteAddress, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // 32 bytes become a 43-character URL-safe Base64 string without padding.
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

    /**
     * Authenticates a raw cookie token when its hash exists, is not revoked and
     * has not expired. Successful use refreshes last_seen_at at most every 15 seconds.
     */
    @Transactional
    Optional<AuthenticatedSession> authenticate(String token) {
        if (token == null || token.length() < 32 || token.length() > 128) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Optional<AuthenticatedSession> session = jdbcClient.sql("""
                        SELECT s.id, u.id AS user_id, u.login, r.rolename
                        FROM auth_sessions s
                        JOIN users u ON u.id = s.user_id
                        JOIN roles r ON r.id = u.role
                        WHERE s.token_hash = :tokenHash
                          AND s.revoked_at IS NULL
                          AND s.expires_at > :now
                        """)
                .param("tokenHash", hash(token))
                .param("now", asTimestamp(now))
                .query((rs, rowNum) -> new AuthenticatedSession(
                        rs.getObject("id", UUID.class),
                        new AuthPrincipal(
                                rs.getLong("user_id"),
                                rs.getString("login"),
                                rs.getString("rolename")
                        )
                ))
                .optional();

        // Throttling avoids a database UPDATE for every asset/API request.
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

    /** Marks a session as revoked; keeping the row provides an audit timestamp. */
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

    /** Counts non-revoked, unexpired sessions active inside {@code activeWindow}. */
    int countConnected() {
        Instant now = Instant.now();
        Long count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM auth_sessions s
                        JOIN users u ON u.id = s.user_id
                        WHERE s.revoked_at IS NULL
                          AND s.expires_at > :now
                          AND s.last_seen_at >= :activeSince
                        """)
                .param("now", asTimestamp(now))
                .param("activeSince", asTimestamp(now.minus(activeWindow)))
                .query(Long.class)
                .single();
        return count == null ? 0 : Math.toIntExact(count);
    }

    private void purgeOldSessions(Instant now) {
        // Expired/revoked rows remain for seven days and are purged on a later login.
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
        // The digest is used as a lookup key; the raw bearer token is never persisted.
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
