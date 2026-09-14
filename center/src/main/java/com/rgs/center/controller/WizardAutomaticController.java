package com.rgs.center.controller;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.web.wizard.WizardAutomaticViewFactory;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final CameraImagingOperations cameraImaging;

    public WizardAutomaticController(
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

    /** @return полная страница подготовки автоматического режима */
    @GetMapping
    public String page(Model model) {
        addView(model);
        return "wizard/automatic-preparation";
    }

    /**
     * Показывает production-монитор цикла без имитации отсутствующего
     * оркестратора и без отправки команд устройствам.
     */
    @GetMapping("/progress")
    public String progress(Model model) {
        addView(model);
        model.addAttribute("wizardTitle", "AUTO MEASUREMENTS — MONITOR");
        return "wizard/automatic-progress";
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
        model.addAttribute("wizardTitle", "AUTO MEASUREMENTS — PREPARATION");
        model.addAttribute("wizardView", homeViewFactory.create(snapshot));
        model.addAttribute(
                "automaticView",
                automaticViewFactory.create(snapshot, cameraImaging.snapshot())
        );
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

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
