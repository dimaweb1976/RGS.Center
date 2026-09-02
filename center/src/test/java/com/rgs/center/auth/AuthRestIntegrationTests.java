package com.rgs.center.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class AuthRestIntegrationTests {

    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void insertOperator() {
        jdbcClient.sql("DELETE FROM auth_sessions").update();
        jdbcClient.sql("DELETE FROM users").update();
        jdbcClient.sql("""
                        INSERT INTO users (login, hash, role)
                        VALUES ('operator', :hash, 3)
                        """)
                .param("hash", passwordEncoder.encode("secret-password"))
                .update();
    }

    @Test
    void csrfLoginMeAndLogoutFormCompleteRestLifecycle() throws Exception {
        MvcResult csrf = requestCsrf();
        String csrfValue = cookieValue(csrf, XSRF_COOKIE);
        Cookie csrfCookie = new Cookie(XSRF_COOKIE, csrfValue);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header(XSRF_HEADER, csrfValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "operator",
                                  "password": "secret-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn();

        String authValue = cookieValue(login, AuthCookieService.COOKIE_NAME);
        Cookie authCookie = new Cookie(AuthCookieService.COOKIE_NAME, authValue);

        mockMvc.perform(get("/api/v1/auth/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.role").value("operator"))
                .andExpect(jsonPath("$.connected").value(1))
                .andExpect(jsonPath("$.multipleConnectionsWarning")
                        .value(false));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(authCookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(403));

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(authCookie, csrfCookie)
                        .header(XSRF_HEADER, csrfValue))
                .andExpect(status().isNoContent())
                .andReturn();
        boolean authCookieDeleted = logout.getResponse()
                .getHeaders("Set-Cookie")
                .stream()
                .anyMatch(value -> value.startsWith(
                                AuthCookieService.COOKIE_NAME + "="
                        ) && value.contains("Max-Age=0"));
        if (!authCookieDeleted) {
            throw new AssertionError("Authentication cookie was not deleted");
        }

        mockMvc.perform(get("/api/v1/auth/me").cookie(authCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));
    }

    @Test
    void loginRequiresCsrfAndRejectsInvalidCredentialsAsJson() throws Exception {
        String body = """
                {"login":"operator","password":"wrong-password"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ));

        MvcResult csrf = requestCsrf();
        String csrfValue = cookieValue(csrf, XSRF_COOKIE);
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie(XSRF_COOKIE, csrfValue))
                        .header(XSRF_HEADER, csrfValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail")
                        .value("Invalid login or password"));
    }

    private MvcResult requestCsrf() throws Exception {
        return mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.cookieName").value(XSRF_COOKIE))
                .andExpect(jsonPath("$.headerName").value(XSRF_HEADER))
                .andReturn();
    }

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse()
                .getHeaders("Set-Cookie")
                .stream()
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring((name + "=").length())
                        .split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " cookie is missing"));
    }
}
