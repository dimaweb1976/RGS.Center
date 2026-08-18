package com.rgs.center.auth;

import java.security.Principal;

/** Authenticated user identity stored in Spring Security for the current request. */
public record AuthPrincipal(long id, String login, String roleName)
        implements Principal {

    @Override
    public String getName() {
        return login;
    }
}
