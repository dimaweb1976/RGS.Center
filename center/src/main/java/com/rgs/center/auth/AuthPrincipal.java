package com.rgs.center.auth;

import java.security.Principal;

public record AuthPrincipal(long id, String login, String displayName)
        implements Principal {

    @Override
    public String getName() {
        return login;
    }
}
