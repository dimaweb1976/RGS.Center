package com.rgs.center.api.auth;

/** Current authenticated user and recently active browser-session count. */
public record CurrentUserResponse(
        String username,
        String role,
        int connected,
        boolean multipleConnectionsWarning
) {
}
