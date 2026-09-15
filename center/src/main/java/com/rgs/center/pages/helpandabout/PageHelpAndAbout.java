package com.rgs.center.pages.helpandabout;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageHelpAndAbout {

    @GetMapping("/helpandabout")
    public String getAutoMode() {
        // Forward internal request to the static file
        return "forward:/F.helpandabout/index.html";
    }
}
