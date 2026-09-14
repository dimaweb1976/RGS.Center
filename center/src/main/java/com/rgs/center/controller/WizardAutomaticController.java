package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.measurement.workflow.MeasurementObjectRequest;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import com.rgs.center.measurement.safety.ScenarioIncidentService;
import com.rgs.center.measurement.safety.ScenarioSafetyMonitor;
import com.rgs.center.web.wizard.WizardAutomaticViewFactory;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardWorkflowViewFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

/** Экран подготовки автоматического измерения нового Thymeleaf-интерфейса. */
@Controller
@RequestMapping("/wizard/automatic")
public class WizardAutomaticController {

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;
    private final WizardAutomaticViewFactory automaticViewFactory;
    private final WizardWorkflowViewFactory workflowViewFactory;
    private final CameraImagingOperations cameraImaging;
    private final ProductionMeasurementOrchestrator orchestrator;
    private final ScenarioIncidentService incidentService;
    private final ScenarioSafetyMonitor safetyMonitor;

    public WizardAutomaticController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory,
            WizardAutomaticViewFactory automaticViewFactory,
            WizardWorkflowViewFactory workflowViewFactory,
            CameraImagingOperations cameraImaging,
            ProductionMeasurementOrchestrator orchestrator,
            ScenarioIncidentService incidentService,
            ScenarioSafetyMonitor safetyMonitor
    ) {
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
        this.automaticViewFactory = automaticViewFactory;
        this.workflowViewFactory = workflowViewFactory;
        this.cameraImaging = cameraImaging;
        this.orchestrator = orchestrator;
        this.incidentService = incidentService;
        this.safetyMonitor = safetyMonitor;
    }

    /** @return полная страница подготовки автоматического режима */
    @GetMapping
    public String page(Model model) {
        addView(model);
        return "wizard/automatic-preparation";
    }

    /** Показывает живой монитор production-оркестратора. */
    @GetMapping("/progress")
    public String progress(Model model) {
        addView(model);
        model.addAttribute("wizardTitle", "AUTO MEASUREMENTS — MONITOR");
        return "wizard/automatic-progress";
    }

    /** Запускает реальный цикл по последнему подтверждённому распознаванию. */
    @PostMapping("/run/start")
    @ResponseBody
    public ResponseEntity<String> startRun() {
        CameraImagingSnapshot camera = cameraImaging.snapshot();
        if (!camera.recognized() || camera.bottles().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    "Recognize at least one bottle before starting the run."
            );
        }
        try {
            var requests = camera.bottles().stream()
                    .map(value -> new MeasurementObjectRequest(
                            value.number(), value.centerX(), value.centerY()
                    ))
                    .toList();
            return ResponseEntity.accepted().body(orchestrator.start(requests).toString());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(errorMessage(exception));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(errorMessage(exception));
        }
    }

    /** Запрашивает паузу на следующей безопасной границе операций. */
    @PostMapping("/run/pause")
    @ResponseBody
    public ResponseEntity<String> pauseRun() {
        return controlResponse(orchestrator.pause(), "Run cannot be paused in its current state.");
    }

    /** Продолжает ранее приостановленный цикл. */
    @PostMapping("/run/resume")
    @ResponseBody
    public ResponseEntity<String> resumeRun() {
        return controlResponse(orchestrator.resume(), "Run cannot be resumed in its current state.");
    }

    /** Запрашивает безопасную остановку цикла и активного оборудования. */
    @PostMapping("/run/stop")
    @ResponseBody
    public ResponseEntity<String> stopRun() {
        return controlResponse(orchestrator.cancel(), "There is no active run to stop.");
    }

    /** Records the authenticated operator's acknowledgement of an incident. */
    @PostMapping("/run/acknowledge")
    @ResponseBody
    public ResponseEntity<String> acknowledgeIncident(Authentication authentication) {
        try {
            safetyMonitor.acknowledge(actor(authentication));
            return ResponseEntity.ok("acknowledged");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(errorMessage(exception));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(errorMessage(exception));
        }
    }

    /** Clears the interlock only after all required equipment is ready again. */
    @PostMapping("/run/reset")
    @ResponseBody
    public ResponseEntity<String> resetIncident(Authentication authentication) {
        try {
            safetyMonitor.resetAfterRecovery(actor(authentication));
            return ResponseEntity.ok("reset");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(errorMessage(exception));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(errorMessage(exception));
        }
    }

    /** Фрагмент для периодического обновления монитора без чтения приборов из HTTP-потока. */
    @GetMapping("/run/status")
    public String runStatus(Model model) {
        addView(model);
        return runMonitorFragment();
    }

    /** Получает тестовый или реальный кадр согласно конфигурации камеры. */
    @PostMapping("/camera/capture")
    public String capture(Model model) {
        runCameraAction(model, cameraImaging::capture);
        return workspaceFragment();
    }

    /** Распознаёт бутылки на последнем полученном кадре. */
    @PostMapping("/camera/recognize")
    public String recognize(Model model) {
        runCameraAction(model, cameraImaging::recognize);
        return workspaceFragment();
    }

    /** Отдаёт текущий оригинальный или размеченный кадр по его версии. */
    @GetMapping("/camera/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(@RequestParam long version) {
        return cameraImaging.currentImage()
                .filter(image -> image.version() == version)
                .map(this::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void runCameraAction(Model model, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            model.addAttribute("wizardActionError", errorMessage(exception));
        }
        addView(model);
    }

    private void addView(Model model) {
        DashboardSnapshot snapshot = dashboardService.snapshot();
        CameraImagingSnapshot camera = cameraImaging.snapshot();
        model.addAttribute("wizardTitle", "AUTO MEASUREMENTS — PREPARATION");
        model.addAttribute("wizardView", homeViewFactory.create(snapshot));
        model.addAttribute(
                "automaticView",
                automaticViewFactory.create(snapshot, camera)
        );
        model.addAttribute(
                "workflowView",
                workflowViewFactory.create(orchestrator.snapshot(), camera.bottles())
        );
        model.addAttribute("scenarioIncident", incidentService.snapshot());
    }

    private ResponseEntity<byte[]> imageResponse(CameraImagePayload image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic().immutable())
                .body(image.content());
    }

    private static String workspaceFragment() {
        return "wizard/fragments/automatic-workspace :: workspace";
    }

    private static String runMonitorFragment() {
        return "wizard/fragments/run-monitor :: monitor";
    }

    private static ResponseEntity<String> controlResponse(
            boolean accepted,
            String rejectedMessage
    ) {
        return accepted
                ? ResponseEntity.accepted().body("accepted")
                : ResponseEntity.status(409).body(rejectedMessage);
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new IllegalStateException("Authenticated operator is required");
        }
        return authentication.getName();
    }
}
