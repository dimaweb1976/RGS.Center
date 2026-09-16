package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.measurement.manual.ManualMeasurementService;
import com.rgs.center.measurement.safety.ScenarioIncidentService;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import com.rgs.center.web.wizard.WizardAutomaticViewFactory;
import com.rgs.center.web.wizard.WizardManualMeasurementViewFactory;
import com.rgs.center.web.wizard.WizardWorkflowViewFactory;
import com.rgs.center.web.wizardnew.WizardNewShellModelService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Independent read-only projections of the existing production workflows. */
@Controller
@RequestMapping("/wizard-new")
public class WizardNewModesController {

    private final WizardNewShellModelService shellModelService;
    private final DashboardSnapshotService dashboardService;
    private final CameraImagingOperations cameraImaging;
    private final WizardAutomaticViewFactory automaticViewFactory;
    private final WizardWorkflowViewFactory workflowViewFactory;
    private final ProductionMeasurementOrchestrator orchestrator;
    private final ScenarioIncidentService incidentService;
    private final ManualMeasurementService manualService;
    private final WizardManualMeasurementViewFactory manualViewFactory;

    public WizardNewModesController(
            WizardNewShellModelService shellModelService,
            DashboardSnapshotService dashboardService,
            CameraImagingOperations cameraImaging,
            WizardAutomaticViewFactory automaticViewFactory,
            WizardWorkflowViewFactory workflowViewFactory,
            ProductionMeasurementOrchestrator orchestrator,
            ScenarioIncidentService incidentService,
            ManualMeasurementService manualService,
            WizardManualMeasurementViewFactory manualViewFactory
    ) {
        this.shellModelService = shellModelService;
        this.dashboardService = dashboardService;
        this.cameraImaging = cameraImaging;
        this.automaticViewFactory = automaticViewFactory;
        this.workflowViewFactory = workflowViewFactory;
        this.orchestrator = orchestrator;
        this.incidentService = incidentService;
        this.manualService = manualService;
        this.manualViewFactory = manualViewFactory;
    }

    @GetMapping("/automatic")
    public String automatic(Model model) {
        addAutomatic(model);
        return "wizard-new/automatic-mode";
    }

    @GetMapping("/automatic/status")
    public String automaticStatus(Model model) {
        addAutomatic(model);
        return "wizard-new/fragments/automatic-workspace :: workspace";
    }

    @GetMapping("/manual")
    public String manual(Model model) {
        addManual(model);
        return "wizard-new/manual-mode";
    }

    @GetMapping("/manual/status")
    public String manualStatus(Model model) {
        addManual(model);
        return "wizard-new/fragments/manual-workspace :: workspace";
    }

    private void addAutomatic(Model model) {
        var dashboard = dashboardService.snapshot();
        var camera = cameraImaging.snapshot();
        shellModelService.addTo(model, "Automatic mode", "auto");
        model.addAttribute("automaticView", automaticViewFactory.create(dashboard, camera));
        model.addAttribute("workflowView", workflowViewFactory.create(
                orchestrator.snapshot(), camera.bottles()
        ));
        model.addAttribute("scenarioIncident", incidentService.snapshot());
    }

    private void addManual(Model model) {
        var dashboard = dashboardService.snapshot();
        shellModelService.addTo(model, "Manual mode", "manual");
        model.addAttribute("manualView", automaticViewFactory.create(
                dashboard, cameraImaging.snapshot()
        ));
        model.addAttribute("manualMeasurement", manualViewFactory.create(
                manualService.snapshot(), dashboard
        ));
    }
}
