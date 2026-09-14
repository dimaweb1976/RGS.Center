package com.rgs.center.controller;

import com.rgs.center.auth.AuthPrincipal;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardSettingsViewFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/** Stage-10 Thymeleaf settings pages backed by the existing settings API. */
@Controller
@RequestMapping("/wizard/settings")
public class WizardSettingsController {

    private final DashboardSnapshotService dashboard;
    private final WizardHomeViewFactory homeViewFactory;
    private final WizardSettingsViewFactory settingsViewFactory;

    public WizardSettingsController(
            DashboardSnapshotService dashboard,
            WizardHomeViewFactory homeViewFactory,
            WizardSettingsViewFactory settingsViewFactory
    ) {
        this.dashboard = dashboard;
        this.homeViewFactory = homeViewFactory;
        this.settingsViewFactory = settingsViewFactory;
    }

    @GetMapping
    public String overview(Model model) {
        addHeader(model, "SETTINGS");
        model.addAttribute("settingsOverview", settingsViewFactory.overview());
        return "wizard/settings";
    }

    @GetMapping("/{deviceCode}")
    public String device(
            @PathVariable String deviceCode,
            Authentication authentication,
            Model model
    ) {
        addHeader(model, "DEVICE SETTINGS");
        try {
            model.addAttribute(
                    "settingsDevice",
                    settingsViewFactory.detail(
                            deviceCode,
                            isAdministrator(authentication)
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        }
        return "wizard/settings-device";
    }

    private void addHeader(Model model, String title) {
        model.addAttribute("wizardTitle", title);
        model.addAttribute(
                "wizardView",
                homeViewFactory.create(dashboard.snapshot())
        );
    }

    private static boolean isAdministrator(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal
                && principal.roleName().equalsIgnoreCase("administrator");
    }
}
