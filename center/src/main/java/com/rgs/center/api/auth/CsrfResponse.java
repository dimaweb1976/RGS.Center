package com.rgs.center.api.auth;

/**
 * Metadata required to submit an unsafe request from JavaScript.
 *
 * <p>The raw token is deliberately read from the {@code XSRF-TOKEN} cookie,
 * while this response tells the frontend which cookie and header to use.</p>
 */
public record CsrfResponse(String cookieName, String headerName) {
}
