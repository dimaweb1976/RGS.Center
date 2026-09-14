package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.web.wizard.WizardAutomaticViewFactory;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Рабочее место ручного режима без дублирования сервисных контроллеров. */
@Controller
@RequestMapping("/wizard/manual")
public class WizardManualController {

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;
    private final WizardAutomaticViewFactory automaticViewFactory;
    private final CameraImagingOperations cameraImaging;

    public WizardManualController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory,
            WizardAutomaticViewFactory automaticViewFactory,
            CameraImagingOperations cameraImaging
    ) {
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
        this.automaticViewFactory = automaticViewFactory;
        this.cameraImaging = cameraImaging;
    }

    /** @return ручной режим с реальными статусами и безопасными переходами */
    @GetMapping
    public String page(Model model) {
        DashboardSnapshot snapshot = dashboardService.snapshot();
        model.addAttribute("wizardTitle", "MANUAL MEASUREMENTS");
        model.addAttribute("wizardView", homeViewFactory.create(snapshot));
        model.addAttribute(
                "manualView",
                automaticViewFactory.create(snapshot, cameraImaging.snapshot())
        );
        return "wizard/manual-mode";
    }
}
