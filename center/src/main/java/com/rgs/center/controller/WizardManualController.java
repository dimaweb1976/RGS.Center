package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.measurement.manual.ManualMeasurementService;
import com.rgs.center.web.wizard.WizardAutomaticViewFactory;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardManualMeasurementViewFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Рабочее место ручного режима без дублирования сервисных контроллеров. */
@Controller
@RequestMapping("/wizard/manual")
public class WizardManualController {

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;
    private final WizardAutomaticViewFactory automaticViewFactory;
    private final WizardManualMeasurementViewFactory measurementViewFactory;
    private final CameraImagingOperations cameraImaging;
    private final ManualMeasurementService measurementService;

    public WizardManualController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory,
            WizardAutomaticViewFactory automaticViewFactory,
            WizardManualMeasurementViewFactory measurementViewFactory,
            CameraImagingOperations cameraImaging,
            ManualMeasurementService measurementService
    ) {
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
        this.automaticViewFactory = automaticViewFactory;
        this.measurementViewFactory = measurementViewFactory;
        this.cameraImaging = cameraImaging;
        this.measurementService = measurementService;
    }

    /** @return ручной режим с реальными статусами и безопасными переходами */
    @GetMapping
    public String page(Model model) {
        addView(model);
        return "wizard/manual-mode";
    }

    @GetMapping("/status")
    public String status(Model model) {
        addView(model);
        return workspaceFragment();
    }

    @PostMapping("/sample-code")
    public String setSampleCode(@RequestParam String sampleCode, Model model) {
        runAction(model, () -> measurementService.setSampleCode(sampleCode));
        return workspaceFragment();
    }

    @PostMapping("/scan")
    public String scan(Model model) {
        runAction(model, measurementService::scanBarcode);
        return workspaceFragment();
    }

    @PostMapping("/weigh")
    public String weigh(Model model) {
        runAction(model, measurementService::readWeight);
        return workspaceFragment();
    }

    @PostMapping("/hatch/open")
    public String openHatch(Model model) {
        runAction(model, measurementService::openHatch);
        return workspaceFragment();
    }

    @PostMapping("/hatch/close")
    public String closeHatch(Model model) {
        runAction(model, measurementService::closeHatch);
        return workspaceFragment();
    }

    @PostMapping("/hv/enable")
    public String enableHighVoltage(Model model) {
        runAction(model, measurementService::enableHighVoltage);
        return workspaceFragment();
    }

    @PostMapping("/hv/disable")
    public String disableHighVoltage(Model model) {
        runAction(model, measurementService::disableHighVoltage);
        return workspaceFragment();
    }

    @PostMapping("/spectrum/start")
    public String startSpectrum(Model model) {
        runAction(model, measurementService::startSpectrumAcquisition);
        return workspaceFragment();
    }

    @PostMapping("/spectrum/stop")
    public String stopSpectrum(Model model) {
        runAction(model, () -> {
            if (!measurementService.stopSpectrumAcquisition()) {
                throw new IllegalStateException("There is no active acquisition to stop");
            }
        });
        return workspaceFragment();
    }

    @PostMapping("/reset")
    public String reset(Model model) {
        runAction(model, measurementService::reset);
        return workspaceFragment();
    }

    private void addView(Model model) {
        DashboardSnapshot snapshot = dashboardService.snapshot();
        model.addAttribute("wizardTitle", "MANUAL MEASUREMENTS");
        model.addAttribute("wizardView", homeViewFactory.create(snapshot));
        model.addAttribute(
                "manualView",
                automaticViewFactory.create(snapshot, cameraImaging.snapshot())
        );
        model.addAttribute(
                "manualMeasurement",
                measurementViewFactory.create(measurementService.snapshot(), snapshot)
        );
    }

    private void runAction(Model model, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            model.addAttribute("wizardActionError", errorMessage(exception));
        }
        addView(model);
    }

    private static String workspaceFragment() {
        return "wizard/fragments/manual-workspace :: workspace";
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
