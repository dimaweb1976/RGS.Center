package com.rgs.center.auth;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
class AuthModelAdvice {

    @ModelAttribute
    void addAuthenticationDetails(
            Authentication authentication,
            Model model
    ) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            model.addAttribute("authUsername", principal.login());
            model.addAttribute("authDisplayName", principal.displayName());
        }
    }
}
