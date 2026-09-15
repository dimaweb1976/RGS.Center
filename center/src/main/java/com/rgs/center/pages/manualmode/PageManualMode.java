package com.rgs.center.pages.manualmode;

import com.rgs.center.pages.automode.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageManualMode {

    @GetMapping("/manualmode")
    public String getAutoMode() {
        // Forward internal request to the static file
        return "forward:/F.manualmode/index.html";
    }
}
