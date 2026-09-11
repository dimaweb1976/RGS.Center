package com.rgs.center.controller;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.measurement.ScenarioStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
/**
 * MVC-контроллер главной страницы и страницы состояния устройств.
 *
 * <p>Контроллер только формирует модель представления. Опрос оборудования
 * выполняют самостоятельные сервисы устройств в фоновых потоках.</p>
 */
public class HomeController {

    private static final Logger log =
            LoggerFactory.getLogger(HomeController.class);

    /** Единый нормализованный снимок для Thymeleaf и других потребителей. */
    private final DashboardSnapshotService dashboardSnapshotService;
    /** Источник состояния автоматического сценария. */
    private final ScenarioStatusService scenarioStatusService;

    /**
     * Создаёт контроллер страниц состояния.
     *
     * @param dashboardSnapshotService единая модель состояния оборудования
     * @param scenarioStatusService состояние автоматического сценария
     */
    public HomeController(
            DashboardSnapshotService dashboardSnapshotService,
            ScenarioStatusService scenarioStatusService
    ) {
        this.dashboardSnapshotService = dashboardSnapshotService;
        this.scenarioStatusService = scenarioStatusService;
    }

    /**
     * Открывает главную страницу приложения.
     *
     * @return имя Thymeleaf-шаблона главной страницы
     */
    @GetMapping("/")
    public String index() {
        log.debug("Rendering application index page");
        return "index";
    }

    /**
     * Формирует полную страницу состояния устройств.
     *
     * @param model модель Thymeleaf, в которую помещается список устройств
     * @return имя шаблона страницы устройств
     */
    @GetMapping("/devices")
    public String devices(Model model) {
        log.debug("Rendering devices page");
        addDashboardStatus(model);
        return "devices";
    }

    /**
     * Возвращает только обновляемый HTML-фрагмент таблицы состояний.
     *
     * <p>Браузер запрашивает этот адрес раз в 10 секунд через JavaScript
     * {@code setInterval(loadStatus, 10000)}.</p>
     *
     * @param model модель Thymeleaf, в которую помещается список устройств
     * @return селектор Thymeleaf-фрагмента таблицы
     */
    @GetMapping("/devices/status")
    public String deviceStatus(Model model) {
        log.trace("Rendering periodic device status fragment");
        addDashboardStatus(model);
        return "fragments/device-status :: status";
    }

    /** Добавляет общий snapshot оборудования и отдельный статус сценария. */
    private void addDashboardStatus(Model model) {
        model.addAttribute(
                "dashboardSnapshot",
                dashboardSnapshotService.snapshot()
        );
        model.addAttribute("scenarioStatus", scenarioStatusService.currentStatus());
    }
}
