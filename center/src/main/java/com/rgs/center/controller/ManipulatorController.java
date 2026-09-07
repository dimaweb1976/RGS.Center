package com.rgs.center.controller;

import com.rgs.center.api.manipulator.ManipulatorStatusQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** MVC-оболочка отдельной read-only страницы манипулятора. */
@Controller
public class ManipulatorController {

    private final ManipulatorStatusQueryService queryService;

    public ManipulatorController(ManipulatorStatusQueryService queryService) {
        this.queryService = queryService;
    }

    /** @return полная страница подробного состояния Dobot */
    @GetMapping("/manipulator")
    public String manipulator(Model model) {
        addStatus(model);
        return "manipulator";
    }

    /** @return обновляемый раз в секунду HTML-фрагмент */
    @GetMapping("/manipulator/status")
    public String manipulatorStatus(Model model) {
        addStatus(model);
        return "fragments/manipulator-status :: status";
    }

    private void addStatus(Model model) {
        model.addAttribute("manipulator", queryService.snapshot());
    }
}
