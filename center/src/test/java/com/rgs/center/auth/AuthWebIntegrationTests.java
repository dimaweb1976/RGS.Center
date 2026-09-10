package com.rgs.center.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
class AuthWebIntegrationTests {

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
    void loginUsesHttpOnlyTokenCookieAndWarnsForTwoBrowsers() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вход в систему")));

        String firstToken = loginAndGetToken();
        Cookie firstCookie = new Cookie(AuthCookieService.COOKIE_NAME, firstToken);

        mockMvc.perform(get("/auth/connections").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("operator"))
                .andExpect(jsonPath("$.connected").value(1))
                .andExpect(jsonPath("$.warning").value(false));

        String secondToken = loginAndGetToken();
        Cookie secondCookie = new Cookie(AuthCookieService.COOKIE_NAME, secondToken);

        mockMvc.perform(get("/auth/connections").cookie(secondCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(2))
                .andExpect(jsonPath("$.warning").value(true));

        // Logout must remain reliable even when an older browser tab has a stale CSRF token.
        MvcResult logout = mockMvc.perform(post("/auth/logout").cookie(firstCookie))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"))
                .andReturn();
        boolean authCookieDeleted = logout.getResponse().getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith(AuthCookieService.COOKIE_NAME + "=")
                        && value.contains("Max-Age=0"));
        if (!authCookieDeleted) {
            throw new AssertionError("Authentication cookie was not deleted");
        }

        mockMvc.perform(get("/").cookie(firstCookie))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void browserGetOnLoginSubmissionUrlRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void javascriptCanPostUsingCurrentXsrfCookieValue() throws Exception {
        String authToken = loginAndGetToken();
        Cookie authCookie = new Cookie(AuthCookieService.COOKIE_NAME, authToken);

        String xsrfSetCookie = mockMvc.perform(get("/measurement-emulator").cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeaders("Set-Cookie")
                .stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF cookie is missing"));

        String xsrfToken = xsrfSetCookie
                .substring("XSRF-TOKEN=".length())
                .split(";", 2)[0];
        Cookie xsrfCookie = new Cookie("XSRF-TOKEN", xsrfToken);

        mockMvc.perform(post("/measurement-emulator/checks/camera/toggle")
                        .cookie(authCookie, xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Камера")));
    }

    private String loginAndGetToken() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();
        Matcher csrfMatcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                .matcher(loginPage.getResponse().getContentAsString());
        if (!csrfMatcher.find()) {
            throw new AssertionError("CSRF form value is missing");
        }
        String csrfToken = csrfMatcher.group(1);
        String xsrfToken = cookieValue(loginPage, "XSRF-TOKEN");

        String setCookie = mockMvc.perform(post("/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", xsrfToken))
                        .param("login", "operator")
                        .param("password", "secret-password")
                        .param("_csrf", csrfToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("HttpOnly; SameSite=Strict")
                ))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        if (setCookie == null) {
            throw new AssertionError("Authentication cookie is missing");
        }
        String pair = setCookie.split(";", 2)[0];
        return pair.substring((AuthCookieService.COOKIE_NAME + "=").length());
    }

    private String cookieValue(MvcResult result, String name) {
        return result.getResponse()
                .getHeaders("Set-Cookie")
                .stream()
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring((name + "=").length()).split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " cookie is missing"));
    }
}
