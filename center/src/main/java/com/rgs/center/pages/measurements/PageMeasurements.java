package com.rgs.center.pages.measurements;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageMeasurements {

    @GetMapping("/measurements")
    public String getAutoMode() {
        // Forward internal request to the static file
        return "forward:/F.measurement/index.html";
    }
}
