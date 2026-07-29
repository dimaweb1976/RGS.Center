package com.rgs.center.controller;

import com.rgs.center.objects.Device;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.RoboPlcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

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

    /** Источник состояния камер. */
    private final CameraService cameraService;
    /** Источник состояния Boson. */
    private final BosonService bosonService;
    /** Источник состояния Nicole. */
    private final NicoleService nicoleService;
    /** Источник состояния RoboPLC. */
    private final RoboPlcService roboPlcService;

    /**
     * Создаёт контроллер страниц состояния.
     *
     * @param cameraService сервис камер
     * @param bosonService сервис Boson
     * @param nicoleService сервис Nicole
     * @param roboPlcService сервис RoboPLC
     */
    public HomeController(
            CameraService cameraService,
            BosonService bosonService,
            NicoleService nicoleService,
            RoboPlcService roboPlcService
    ) {
        this.cameraService = cameraService;
        this.bosonService = bosonService;
        this.nicoleService = nicoleService;
        this.roboPlcService = roboPlcService;
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
        model.addAttribute("devices", getVisibleDevices());
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
        model.addAttribute("devices", getVisibleDevices());
        return "fragments/device-status :: status";
    }

    /**
     * Собирает устройства в порядке отображения на странице.
     *
     * @return новый изменяемый список видимых устройств
     */
    private List<Device> getVisibleDevices() {
        // Список камер может быть расширен, не затрагивая добавление остальных устройств.
        List<Device> devices = new ArrayList<>(cameraService.getCameras());
        devices.add(roboPlcService.getRoboPlc());
        devices.add(nicoleService.getNicole());
        devices.add(bosonService.getBoson());
        return devices;
    }
}
