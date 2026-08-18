package com.rgs.center.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Loads the user together with the referenced role required for authentication. */
@Repository
class AuthUserRepository {

    private final JdbcClient jdbcClient;

    AuthUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Finds one user by the unique {@code users.login} value and resolves
     * {@code users.role -> roles.id} in the same query.
     */
    Optional<AuthUser> findByLogin(String login) {
        return jdbcClient.sql("""
                        SELECT u.id, u.login, u.hash, r.id AS role_id, r.rolename
                        FROM users u
                        JOIN roles r ON r.id = u.role
                        WHERE u.login = :login
                        """)
                .param("login", login)
                .query((rs, rowNum) -> new AuthUser(
                        rs.getLong("id"),
                        rs.getString("login"),
                        rs.getString("hash"),
                        rs.getInt("role_id"),
                        rs.getString("rolename")
                ))
                .optional();
    }
}
