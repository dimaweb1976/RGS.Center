package com.rgs.center.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.rgs.center.web.wizardnew.WizardNewShellModelService;

/** Independent entry point for the new visual shell; the existing Wizard remains intact. */
@Controller
@RequestMapping("/wizard-new")
public class WizardNewHomeController {

    private final WizardNewShellModelService shellModelService;

    public WizardNewHomeController(WizardNewShellModelService shellModelService) {
        this.shellModelService = shellModelService;
    }

    @GetMapping({"", "/"})
    public String home(Model model) {
        shellModelService.addTo(model, "Main menu", "home");
        return "wizard-new/main-menu";
    }

    /** Updated rail fragment; reads the warehouse, not device transports. */
    @GetMapping("/status")
    public String status(Model model) {
        model.addAttribute("wizardNewShell", shellModelService.snapshot());
        return "wizard-new/fragments/shell :: liveStatus";
    }
}
