package com.rgs.center.auth;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the authenticated login and role to every Thymeleaf model. */
@ControllerAdvice
class AuthModelAdvice {

    /** Exposes {@code authUsername} and {@code authRole} for shared page fragments. */
    @ModelAttribute
    void addAuthenticationDetails(
            Authentication authentication,
            Model model
    ) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            model.addAttribute("authUsername", principal.login());
            model.addAttribute("authRole", principal.roleName());
        }
    }
}
