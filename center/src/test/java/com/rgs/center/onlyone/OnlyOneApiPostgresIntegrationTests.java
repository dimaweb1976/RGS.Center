package com.rgs.center.onlyone;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the exclusive-access REST API against the configured PostgreSQL
 * database. Every test runs in a rollback-only transaction, so the singleton
 * lock row is restored even when an assertion fails.
 *
 * <p>Enable explicitly with {@code RGSCENTER_POSTGRES_TESTS=true}. Database
 * settings are read from process variables first and then from the repository
 * {@code .env} file.</p>
 */
@Tag("postgres")
@EnabledIfEnvironmentVariable(
        named = "RGSCENTER_POSTGRES_TESTS",
        matches = "(?i)true"
)
@SpringBootTest(
        properties = {
                "spring.sql.init.mode=never",
                "rgs.device.camera.enabled=false",
                "rgs.device.lenovo-pc.enabled=false",
                "rgs.device.nicole.enabled=false",
                "rgs.device.boson.enabled=false",
                "rgs.device.manipulator.enabled=false",
                "rgs.device.barcode-scanner.enabled=false",
                "rgs.device.gdrm.enabled=false",
                "rgs.device.scales.enabled=false",
                "rgs.device.shp.enabled=false",
                "rgs.device.gamma-pro.enabled=false",
                "rgs.only-one.enforce=false",
                "TTL_MINUTES=5"
        }
)
@AutoConfigureMockMvc
@Transactional
@Import(OnlyOneApiPostgresIntegrationTests.PasswordTestConfiguration.class)
class OnlyOneApiPostgresIntegrationTests {

    private static final Map<String, String> DATABASE = loadDatabaseSettings();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + required("RGSCENTER_POSTGRES_DB_HOST") + ":"
                + required("RGSCENTER_POSTGRES_DB_PORT") + "/"
                + required("RGSCENTER_DBNAME"));
        registry.add(
                "spring.datasource.username",
                () -> required("RGSCENTER_DEDICATED_APP_USER_NAME")
        );
        registry.add(
                "spring.datasource.password",
                () -> required("RGSCENTER_DEDICATED_APP_USER_PASSWORD")
        );
    }

    @BeforeEach
    void prepareSingletonLockRow() {
        int rows = jdbcClient.sql("""
                        UPDATE onlyone_lock
                        SET token = NULL,
                            client_ip = NULL,
                            expires_at = NULL
                        WHERE id = 1
                        """)
                .update();
        if (rows != 1) {
            throw new IllegalStateException(
                    "onlyone_lock must contain exactly one row with id = 1"
            );
        }
    }

    @Test
    void completeLeaseLifecycleIsPersistedInPostgres() throws Exception {
        String responseBody = mockMvc.perform(post("/api/onlyOne/enter")
                        .header("X-Forwarded-For", "192.168.1.10, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"masterpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(responseBody, "$.token");

        assertThat(databaseToken()).isEqualTo(token);
        assertThat(databaseClientIp()).isEqualTo("192.168.1.10");
        assertThat(databaseLeaseIsActive()).isTrue();

        mockMvc.perform(get("/api/onlyOne/checkBusy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busy").value(true))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.10"))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(get("/api/onlyOne/checkMe")
                        .header("X-Lock-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/onlyOne/updateBusy")
                        .header("X-Lock-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("extended"));
        assertThat(databaseLeaseIsActive()).isTrue();

        mockMvc.perform(post("/api/onlyOne/exit")
                        .header("X-Lock-Token", token))
                .andExpect(status().isNoContent());
        assertThat(databaseToken()).isNull();
        assertThat(databaseClientIp()).isNull();
    }

    @Test
    void activeDatabaseLeaseRejectsACompetingEnter() throws Exception {
        setDatabaseLease("owner-token", "192.168.1.20", "5 minutes");

        mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"masterpass\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("System is busy"));

        assertThat(databaseToken()).isEqualTo("owner-token");
        assertThat(databaseClientIp()).isEqualTo("192.168.1.20");
    }

    @Test
    void expiredDatabaseLeaseCanBeTakenOver() throws Exception {
        setDatabaseLease("expired-token", "192.168.1.30", "-1 minute");

        String responseBody = mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"masterpass\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String newToken = JsonPath.read(responseBody, "$.token");

        assertThat(newToken).isNotEqualTo("expired-token");
        assertThat(databaseToken()).isEqualTo(newToken);
        assertThat(databaseLeaseIsActive()).isTrue();
    }

    @Test
    void expiredDatabaseLeaseFailsCheckAndHeartbeat() throws Exception {
        setDatabaseLease("expired-token", "192.168.1.30", "-1 minute");

        mockMvc.perform(get("/api/onlyOne/checkMe")
                        .header("X-Lock-Token", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/onlyOne/updateBusy")
                        .header("X-Lock-Token", "expired-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/onlyOne/checkBusy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busy").value(false))
                .andExpect(jsonPath("$.busyUntil").isEmpty())
                .andExpect(jsonPath("$.clientIp").isEmpty());
    }

    @Test
    void invalidPasswordDoesNotChangeTheDatabaseRow() throws Exception {
        mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid password"));

        assertThat(databaseToken()).isNull();
        assertThat(databaseClientIp()).isNull();
    }

    private void setDatabaseLease(String token, String clientIp, String interval) {
        jdbcClient.sql("""
                        UPDATE onlyone_lock
                        SET token = :token,
                            client_ip = :clientIp,
                            expires_at = LOCALTIMESTAMP
                                + CAST(:leaseInterval AS INTERVAL)
                        WHERE id = 1
                        """)
                .param("token", token)
                .param("clientIp", clientIp)
                .param("leaseInterval", interval)
                .update();
    }

    private String databaseToken() {
        return jdbcClient.sql("SELECT token FROM onlyone_lock WHERE id = 1")
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private String databaseClientIp() {
        return jdbcClient.sql("SELECT client_ip FROM onlyone_lock WHERE id = 1")
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private boolean databaseLeaseIsActive() {
        return jdbcClient.sql("""
                        SELECT token IS NOT NULL
                               AND expires_at >= LOCALTIMESTAMP
                        FROM onlyone_lock
                        WHERE id = 1
                        """)
                .query(Boolean.class)
                .single();
    }

    private static String required(String key) {
        String value = DATABASE.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is not configured");
        }
        return value;
    }

    private static Map<String, String> loadDatabaseSettings() {
        Map<String, String> settings = new HashMap<>(System.getenv());
        Path envFile = List.of(Path.of(".env"), Path.of("..", ".env"))
                .stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        if (envFile == null) {
            return settings;
        }

        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = removeMatchingQuotes(
                        trimmed.substring(separator + 1).trim()
                );
                settings.putIfAbsent(key, value);
            }
            return settings;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read database settings from " + envFile,
                    exception
            );
        }
    }

    private static String removeMatchingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '\"' && last == '\"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PasswordTestConfiguration {

        @Bean
        @Primary
        MasterPasswordVerifier testMasterPasswordVerifier(
                PasswordEncoder passwordEncoder
        ) {
            return new MasterPasswordVerifier(
                    passwordEncoder,
                    passwordEncoder.encode("masterpass")
            );
        }
    }
}
