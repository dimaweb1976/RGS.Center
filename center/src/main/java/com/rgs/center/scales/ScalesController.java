package com.rgs.center.scales;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Thymeleaf endpoints for the live scales page. */
@Controller
@RequestMapping("/scales")
public class ScalesController {

    private final ScalesService scalesService;

    public ScalesController(ScalesService scalesService) {
        this.scalesService = scalesService;
    }

    @GetMapping
    public String getScales(Model model) {
        addSnapshot(model);
        return "scales";
    }

    @GetMapping("/status")
    public String getScalesStatus(Model model) {
        addSnapshot(model);
        return "fragments/scales-status :: status";
    }

    private void addSnapshot(Model model) {
        model.addAttribute(
                "scales",
                ScalesViewModel.from(scalesService.snapshot())
        );
    }
}
