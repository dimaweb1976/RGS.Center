package com.rgs.center.pages.settings;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageSettings {

    @GetMapping("/settings")
    public String getAutoMode() {
        // Forward internal request to the static file
        return "forward:/F.settings/index.html";
    }
}
