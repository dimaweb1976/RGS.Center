package com.rgs.center.onlyone;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
class OnlyOneLockRepository {

    private final JdbcClient jdbcClient;

    OnlyOneLockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    LocalDateTime tryEnter(
            String newToken,
            String clientIp,
            int ttlMinutes
    ) {
        List<LocalDateTime> results = jdbcClient.sql("""
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
                .list();

        return results.isEmpty() ? null : results.get(0);
    }

    LocalDateTime tryExtend(String currentToken, int ttlMinutes) {
        List<LocalDateTime> results = jdbcClient.sql("""
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
                .list();

        return results.isEmpty() ? null : results.get(0);
    }

    void exit(String currentToken) {
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
        List<OnlyOneLockState> results = jdbcClient.sql("""
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
                .list();

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "onlyone_lock must contain the singleton row with id = 1"
            );
        }
        return results.get(0);
    }

    LocalDateTime findActiveExpiry(String currentToken) {
        List<LocalDateTime> results = jdbcClient.sql("""
                        SELECT expires_at
                        FROM onlyone_lock
                        WHERE id = 1
                          AND token = :currentToken
                          AND expires_at >= LOCALTIMESTAMP
                        """)
                .param("currentToken", currentToken)
                .query(LocalDateTime.class)
                .list();

        return results.isEmpty() ? null : results.get(0);
    }
}

record OnlyOneLockState(
        String token,
        String clientIp,
        LocalDateTime expiresAt,
        boolean busy
) {
}