package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.measurement.stepdiagnostic.StepDiagnosticService;
import com.rgs.center.measurement.stepdiagnostic.StepDiagnosticSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;

/** Independent step diagnostic; only a confirmed barcode scan may send a command. */
@Controller
@RequestMapping("/wizard/automatic/step-test")
public class WizardStepDiagnosticController {

    private final StepDiagnosticService diagnosticService;
    private final CameraImagingOperations cameraImaging;
    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;

    public WizardStepDiagnosticController(
            StepDiagnosticService diagnosticService,
            CameraImagingOperations cameraImaging,
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory
    ) {
        this.diagnosticService = diagnosticService;
        this.cameraImaging = cameraImaging;
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("wizardTitle", "AUTO MEASUREMENTS — STEP TEST");
        model.addAttribute("wizardView", homeViewFactory.create(dashboardService.snapshot()));
        addDiagnosticView(model);
        return "wizard/step-diagnostic";
    }

    @GetMapping("/status")
    public String status(Model model) {
        addDiagnosticView(model);
        return "wizard/fragments/step-diagnostic :: panel";
    }

    @PostMapping("/session")
    @ResponseBody
    public ResponseEntity<String> createSession() {
        CameraImagingSnapshot camera = cameraImaging.snapshot();
        boolean recognized = camera.recognized() && !camera.bottles().isEmpty();
        List<Integer> objects = recognized
                ? camera.bottles().stream().map(value -> value.number()).toList()
                : List.of(1); // Explicit diagnostic placeholder, not a detected bottle.
        try {
            return ResponseEntity.accepted().body(
                    diagnosticService.createSession(objects, recognized).id().toString()
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(exception.getMessage());
        }
    }

    @PostMapping("/step")
    @ResponseBody
    public ResponseEntity<String> startStep(
            @RequestParam UUID sessionId,
            @RequestParam int objectNumber,
            @RequestParam MeasurementWorkflowStep step,
            @RequestParam(defaultValue = "false") boolean samplePositionConfirmed
    ) {
        try {
            StepDiagnosticSnapshot snapshot = diagnosticService.startStep(
                    sessionId, objectNumber, step, samplePositionConfirmed
            );
            String status = snapshot.entries().stream()
                    .filter(entry -> entry.objectNumber() == objectNumber && entry.step() == step)
                    .findFirst().orElseThrow().status().name();
            return "RUNNING".equals(status)
                    ? ResponseEntity.accepted().body(status)
                    : ResponseEntity.ok(status);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(exception.getMessage());
        }
    }

    @PostMapping("/step/skip-error")
    @ResponseBody
    public ResponseEntity<String> skipErroredStep(
            @RequestParam UUID sessionId,
            @RequestParam int objectNumber
    ) {
        try {
            diagnosticService.skipErroredStep(sessionId, objectNumber);
            return ResponseEntity.ok("SKIPPED");
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(exception.getMessage());
        }
    }

    @PostMapping("/scan/cancel")
    @ResponseBody
    public ResponseEntity<String> cancelScan(@RequestParam UUID sessionId) {
        return diagnosticService.cancelActiveScan(sessionId)
                ? ResponseEntity.accepted().body("cancelling")
                : ResponseEntity.status(409).body("No active diagnostic scan to cancel");
    }

    private void addDiagnosticView(Model model) {
        StepDiagnosticSnapshot snapshot = diagnosticService.snapshot().orElse(null);
        model.addAttribute("stepTestSession", snapshot);
        model.addAttribute("stepTestSteps", snapshot == null ? List.of() : snapshot.focusSteps());
    }
}
