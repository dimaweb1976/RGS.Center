package com.rgs.center.auth;

/** Database projection of {@code users JOIN roles} used during password login. */
record AuthUser(
        long id,
        String login,
        String passwordHash,
        int roleId,
        String roleName
) {
}
