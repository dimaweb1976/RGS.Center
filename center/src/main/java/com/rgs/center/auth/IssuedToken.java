package com.rgs.center.auth;

import java.time.Instant;

record IssuedToken(String value, Instant expiresAt) {
}
