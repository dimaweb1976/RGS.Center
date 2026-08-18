package com.rgs.center.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/** Reads, writes and expires the browser cookie containing the raw bearer token. */
@Component
class AuthCookieService {

    /** Authentication cookie name shared by the controller and request filter. */
    static final String COOKIE_NAME = "RGS_AUTH_TOKEN";

    private final boolean secure;

    AuthCookieService(@Value("${rgs.auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    /** Returns the first RGSCenter authentication cookie from the request. */
    Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    /** Writes an HttpOnly cookie whose Max-Age matches {@link IssuedToken#expiresAt()}. */
    void write(HttpServletResponse response, IssuedToken token) {
        Duration maxAge = Duration.between(Instant.now(), token.expiresAt());
        ResponseCookie cookie = baseCookie(token.value())
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Instructs the browser to remove the authentication cookie immediately. */
    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                baseCookie("").maxAge(Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        // SameSite=Strict limits cross-site delivery; Secure must be enabled for HTTPS.
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
