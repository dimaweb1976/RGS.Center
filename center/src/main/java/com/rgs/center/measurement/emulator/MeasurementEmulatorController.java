package com.rgs.center.measurement.emulator;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTML endpoint эмулятора алгоритма измерения. */
@Controller
@RequestMapping("/measurement-emulator")
public class MeasurementEmulatorController {

    private final MeasurementEmulatorService emulatorService;

    /** @param emulatorService автомат состояний, не связанный с реальными портами */
    public MeasurementEmulatorController(MeasurementEmulatorService emulatorService) {
        this.emulatorService = emulatorService;
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

    /** Эмулирует автоматическое распознавание объектов камерой. */
    @PostMapping("/detect-objects")
    public String detectObjects(Model model) {
        emulatorService.detectObjects();
        return status(model);
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
}
