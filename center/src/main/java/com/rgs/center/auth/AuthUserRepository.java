package com.rgs.center.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class AuthUserRepository {

    private final JdbcClient jdbcClient;

    AuthUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<AuthUser> findByLogin(String login) {
        return jdbcClient.sql("""
                        SELECT id, login, treename, scrthsh, active
                        FROM appusers
                        WHERE login = :login
                        """)
                .param("login", login)
                .query((rs, rowNum) -> new AuthUser(
                        rs.getLong("id"),
                        rs.getString("login"),
                        rs.getString("treename"),
                        rs.getString("scrthsh"),
                        rs.getBoolean("active")
                ))
                .optional();
    }
}
