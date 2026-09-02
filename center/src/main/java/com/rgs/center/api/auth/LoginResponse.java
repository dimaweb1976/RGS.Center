package com.rgs.center.api.auth;

import java.time.Instant;

/**
 * Successful creation of a server-side authentication session.
 *
 * @param authenticated always {@code true} for a successful response
 * @param expiresAt absolute expiration time of the HttpOnly authentication cookie
 */
public record LoginResponse(boolean authenticated, Instant expiresAt) {
}
