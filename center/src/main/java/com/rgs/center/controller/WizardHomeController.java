package com.rgs.center.controller;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Первый read-only экран нового Thymeleaf-интерфейса RGSCenter. */
@Controller
@RequestMapping("/wizard")
public class WizardHomeController {

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory viewFactory;

    public WizardHomeController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory viewFactory
    ) {
        this.dashboardService = dashboardService;
        this.viewFactory = viewFactory;
    }

    /** @return новое главное меню, не заменяющее существующий endpoint {@code /} */
    @GetMapping({"", "/"})
    public String home(Model model) {
        addView(model);
        model.addAttribute("wizardTitle", "MAIN MENU");
        return "wizard/main-menu";
    }

    /** @return обновляемая строка состояния и предупреждений */
    @GetMapping("/status")
    public String status(Model model) {
        addView(model);
        return "wizard/fragments/live-status :: liveStatus";
    }

    private void addView(Model model) {
        model.addAttribute(
                "wizardView",
                viewFactory.create(dashboardService.snapshot())
        );
    }
}
