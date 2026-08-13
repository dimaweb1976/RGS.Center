package com.rgs.center.measurement.emulator;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingOperations;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

/** HTML endpoint эмулятора алгоритма измерения. */
@Controller
@RequestMapping("/measurement-emulator")
public class MeasurementEmulatorController {

    private final MeasurementEmulatorService emulatorService;
    private final CameraImagingOperations cameraImaging;

    /**
     * @param emulatorService автомат состояний сценария
     * @param cameraImaging сервис изображения для бинарного HTTP endpoint
     */
    public MeasurementEmulatorController(
            MeasurementEmulatorService emulatorService,
            CameraImagingOperations cameraImaging
    ) {
        this.emulatorService = emulatorService;
        this.cameraImaging = cameraImaging;
    }

    /** @return полная страница эмулятора */
    @GetMapping
    public String emulator(Model model) {
        addSnapshot(model);
        return "measurement-emulator";
    }

    /** @return обновляемый HTML-фрагмент состояния */
    @GetMapping("/status")
    public String status(Model model) {
        addSnapshot(model);
        return "fragments/measurement-emulator-status :: status";
    }

    /** Сбрасывает сценарий и возвращает обновлённый фрагмент. */
    @PostMapping("/reset")
    public String reset(Model model) {
        emulatorService.reset();
        return status(model);
    }

    /** Получает новый тестовый или реальный кадр камеры. */
    @PostMapping("/camera/capture")
    public String captureCameraImage(Model model) {
        emulatorService.captureCameraImage();
        return status(model);
    }

    /** Распознаёт бутылки на последнем полученном кадре. */
    @PostMapping({"/detect-objects", "/camera/recognize"})
    public String detectObjects(Model model) {
        emulatorService.recognizeBottles();
        return status(model);
    }

    /** Отдаёт оригинал или размеченную JPEG-копию окна камеры. */
    @GetMapping("/camera/image")
    @ResponseBody
    public ResponseEntity<byte[]> cameraImage(@RequestParam long version) {
        return cameraImaging.currentImage()
                .filter(image -> image.version() == version)
                .map(this::imageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Запускает тело цикла по готовому списку. */
    @PostMapping("/start")
    public String start(Model model) {
        emulatorService.startCycle();
        return status(model);
    }

    /** Инвертирует выбранную проверку для демонстрации оповещения. */
    @PostMapping("/checks/{code}/toggle")
    public String toggleCheck(@PathVariable String code, Model model) {
        emulatorService.toggleCheck(code);
        return status(model);
    }

    /** Имитирует нормальный или превышенный ток утечки. */
    @PostMapping("/leakage/toggle")
    public String toggleLeakage(Model model) {
        emulatorService.toggleLeakageCurrent();
        return status(model);
    }

    private void addSnapshot(Model model) {
        model.addAttribute("emulator", emulatorService.snapshot());
    }

    private ResponseEntity<byte[]> imageResponse(CameraImagePayload image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image.content());
    }
}
