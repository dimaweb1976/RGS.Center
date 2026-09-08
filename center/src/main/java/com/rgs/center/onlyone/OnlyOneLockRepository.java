package com.rgs.center.onlyone;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Performs lease decisions atomically against the singleton PostgreSQL row.
 *
 * <p>{@code onlyone_lock} must contain exactly one row with {@code id = 1}.
 * Conditional {@code UPDATE} statements make acquisition and extension a
 * single database operation, so two concurrent application requests cannot
 * both acquire the lease.</p>
 *
 * <p>PostgreSQL {@code LOCALTIMESTAMP} is the authoritative clock. Per the
 * lease contract, {@code expires_at >= LOCALTIMESTAMP} is active and
 * {@code expires_at < LOCALTIMESTAMP} is expired.</p>
 */
@Repository
class OnlyOneLockRepository {

    private final JdbcClient jdbcClient;

    OnlyOneLockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<LocalDateTime> tryEnter(
            String newToken,
            String clientIp,
            int ttlMinutes
    ) {
        // Acquisition succeeds only when the row is unowned or its previous
        // lease is strictly expired. UPDATE ... RETURNING yields no row when
        // another client still owns an active lease.
        return jdbcClient.sql("""
                        UPDATE onlyone_lock
                        SET token = :newToken,
                            client_ip = :clientIp,
                            expires_at = LOCALTIMESTAMP
                                + (:ttlMinutes || ' minutes')::INTERVAL
                        WHERE id = 1
                          AND (token IS NULL OR expires_at < LOCALTIMESTAMP)
                        RETURNING expires_at
                        """)
                .param("newToken", newToken)
                .param("clientIp", clientIp)
                .param("ttlMinutes", ttlMinutes)
                .query(LocalDateTime.class)
                .optional();
    }

    Optional<LocalDateTime> tryExtend(String currentToken, int ttlMinutes) {
        // The matching token and active-time predicate are checked by the same
        // UPDATE that changes expires_at. A late heartbeat cannot resurrect an
        // expired lease after another client becomes eligible to acquire it.
        return jdbcClient.sql("""
                        UPDATE onlyone_lock
                        SET expires_at = LOCALTIMESTAMP
                            + (:ttlMinutes || ' minutes')::INTERVAL
                        WHERE id = 1
                          AND token = :currentToken
                          AND expires_at >= LOCALTIMESTAMP
                        RETURNING expires_at
                        """)
                .param("currentToken", currentToken)
                .param("ttlMinutes", ttlMinutes)
                .query(LocalDateTime.class)
                .optional();
    }

    void exit(String currentToken) {
        // Token matching prevents one client from clearing another client's
        // lease. Zero updated rows is an accepted idempotent outcome.
        jdbcClient.sql("""
                        UPDATE onlyone_lock
                        SET token = NULL,
                            client_ip = NULL,
                            expires_at = NULL
                        WHERE id = 1
                          AND token = :currentToken
                        """)
                .param("currentToken", currentToken)
                .update();
    }

    OnlyOneLockState readState() {
        // Compute busy inside PostgreSQL to apply the same clock and inclusive
        // expiry boundary used by acquisition and token validation.
        return jdbcClient.sql("""
                        SELECT token,
                               client_ip,
                               expires_at,
                               token IS NOT NULL
                                   AND expires_at >= LOCALTIMESTAMP AS busy
                        FROM onlyone_lock
                        WHERE id = 1
                        """)
                .query((resultSet, rowNumber) -> new OnlyOneLockState(
                        resultSet.getString("token"),
                        resultSet.getString("client_ip"),
                        resultSet.getObject("expires_at", LocalDateTime.class),
                        resultSet.getBoolean("busy")
                ))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "onlyone_lock must contain the singleton row with id = 1"
                ));
    }

    Optional<LocalDateTime> findActiveExpiry(String currentToken) {
        // Returning an expiry only for an active matching token is the central
        // ownership check used by checkMe and the backend interceptor.
        return jdbcClient.sql("""
                        SELECT expires_at
                        FROM onlyone_lock
                        WHERE id = 1
                          AND token = :currentToken
                          AND expires_at >= LOCALTIMESTAMP
                        """)
                .param("currentToken", currentToken)
                .query(LocalDateTime.class)
                .optional();
    }
}

/** Internal snapshot of the singleton row and its database-computed busy flag. */
record OnlyOneLockState(
        String token,
        String clientIp,
        LocalDateTime expiresAt,
        boolean busy
) {
}
