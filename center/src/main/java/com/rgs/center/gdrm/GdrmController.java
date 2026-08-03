package com.rgs.center.gdrm;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTML endpoint текущего состояния Gamma Dose Rate Meter. */
@Controller
@RequestMapping("/gdrm")
public class GdrmController {

    private final GdrmService gdrmService;

    /** @param gdrmService фоновый сервис прибора GDRM */
    public GdrmController(GdrmService gdrmService) {
        this.gdrmService = gdrmService;
    }

    /**
     * Открывает страницу прибора, не блокируя web-поток чтением COM3.
     *
     * @param model данные для шаблона
     * @return имя полной HTML-страницы
     */
    @GetMapping
    public String getGdrm(Model model) {
        addSnapshot(model);
        return "gdrm";
    }

    /**
     * Возвращает готовый HTML-фрагмент для периодического обновления страницы.
     *
     * @param model данные для шаблона
     * @return имя Thymeleaf-фрагмента
     */
    @GetMapping("/status")
    public String getGdrmStatus(Model model) {
        addSnapshot(model);
        return "fragments/gdrm-status :: status";
    }

    /** Добавляет отформатированный снимок фонового сервиса в модель страницы. */
    private void addSnapshot(Model model) {
        model.addAttribute("gdrm", GdrmViewModel.from(gdrmService.snapshot()));
    }
}
