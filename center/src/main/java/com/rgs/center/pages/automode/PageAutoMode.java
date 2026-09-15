package com.rgs.center.pages.automode;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageAutoMode {

    @GetMapping("/automode")
    public String getAutoMode() {
        // Forward internal request to the static file
        return "forward:/F.automode/index.html";
    }
}
