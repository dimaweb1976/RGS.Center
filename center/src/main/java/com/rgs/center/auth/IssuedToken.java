package com.rgs.center.auth;

import java.time.Instant;

/** Raw token returned once to the browser and its absolute expiration timestamp. */
record IssuedToken(String value, Instant expiresAt) {
}
