package com.rgs.center.pages.debug;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingPage {
    @GetMapping("/")
    public String redirectToAutoMode() {
        return "redirect:/F.automode/index.html";
    }    
}
