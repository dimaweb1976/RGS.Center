package com.rgs.center.auth;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSessionServiceTests {

    private JdbcClient jdbcClient;
    private AuthSessionService sessionService;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:auth-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"))
                .execute(dataSource);

        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("DELETE FROM auth_sessions").update();
        jdbcClient.sql("DELETE FROM users").update();
        sessionService = new AuthSessionService(
                jdbcClient,
                Duration.ofHours(8),
                Duration.ofSeconds(90)
        );
        authenticationService = new AuthenticationService(
                new AuthUserRepository(jdbcClient),
                sessionService,
                new BCryptPasswordEncoder()
        );
    }

    @Test
    void issuesIndependentTokensAndWarnsForSecondConnectedBrowser() {
        insertUser("operator", "secret-password", 3);

        IssuedToken first = authenticationService.login(
                "operator", "secret-password", "127.0.0.1", "browser-one");
        IssuedToken second = authenticationService.login(
                "operator", "secret-password", "127.0.0.1", "browser-two");

        assertNotEquals(first.value(), second.value());
        assertEquals(2, sessionService.countConnected());
        assertEquals("operator", sessionService.authenticate(first.value())
                .orElseThrow().principal().login());
        assertEquals("operator", sessionService.authenticate(first.value())
                .orElseThrow().principal().roleName());

        sessionService.revoke(first.value());
        assertTrue(sessionService.authenticate(first.value()).isEmpty());
        assertEquals(1, sessionService.countConnected());
    }

    @Test
    void rejectsWrongPasswordAndUnknownUser() {
        insertUser("operator", "secret-password", 3);

        assertThrows(BadCredentialsException.class, () ->
                authenticationService.login(
                        "operator", "wrong", "127.0.0.1", "browser"));
        assertThrows(BadCredentialsException.class, () ->
                authenticationService.login(
                        "missing", "secret-password", "127.0.0.1", "browser"));
    }

    private void insertUser(String login, String password, int role) {
        String hash = new BCryptPasswordEncoder().encode(password);
        jdbcClient.sql("""
                        INSERT INTO users (login, hash, role)
                        VALUES (:login, :hash, :role)
                        """)
                .param("login", login)
                .param("hash", hash)
                .param("role", role)
                .update();
    }
}
