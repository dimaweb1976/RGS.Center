package com.rgs.center.auth;

record AuthUser(
        long id,
        String login,
        String displayName,
        String passwordHash,
        boolean active
) {
}
