package com.rgs.center.controller;

import com.rgs.center.gammapro.GammaProDeviceView;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.DeviceViewService;
import com.rgs.center.service.LenovoPcService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.shp.ShpService;
import com.rgs.center.shp.ShpStatusEvaluator;
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
    /** Источник состояния операторского компьютера LenovoPC. */
    private final LenovoPcService lenovoPcService;
    /** Формирует общее представление COM-устройств из витрины. */
    private final DeviceViewService deviceViewService;
    /** Источник последних успешно полученных показаний SHP. */
    private final ShpService shpService;
    /** Рассчитывает READY/ALERT по последнему снимку SHP. */
    private final ShpStatusEvaluator shpStatusEvaluator;
    /** Источник фактического состояния TCP-сокета Gamma-PRO. */
    private final GammaProService gammaProService;

    /**
     * Создаёт контроллер страниц состояния.
     *
     * @param cameraService сервис камер
     * @param bosonService сервис Boson
     * @param nicoleService сервис Nicole
     * @param lenovoPcService сервис операторского компьютера LenovoPC
     * @param deviceViewService представление состояния COM-устройств
     * @param shpService фоновый сервис предусилителя SHP
     * @param shpStatusEvaluator расчёт технологического статуса SHP
     * @param gammaProService фактическое состояние TCP-соединения Gamma-PRO
     */
    public HomeController(
            CameraService cameraService,
            BosonService bosonService,
            NicoleService nicoleService,
            LenovoPcService lenovoPcService,
            DeviceViewService deviceViewService,
            ShpService shpService,
            ShpStatusEvaluator shpStatusEvaluator,
            GammaProService gammaProService
    ) {
        this.cameraService = cameraService;
        this.bosonService = bosonService;
        this.nicoleService = nicoleService;
        this.lenovoPcService = lenovoPcService;
        this.deviceViewService = deviceViewService;
        this.shpService = shpService;
        this.shpStatusEvaluator = shpStatusEvaluator;
        this.gammaProService = gammaProService;
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
        addDeviceStatus(model);
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
        addDeviceStatus(model);
        return "fragments/device-status :: status";
    }

    /** Добавляет общий список устройств и последние снимки NICOLE и SHP. */
    private void addDeviceStatus(Model model) {
        model.addAttribute("devices", getVisibleDevices());
        model.addAttribute(
                "gammaProDevice",
                GammaProDeviceView.from(
                        deviceViewService.getDevice(DeviceCode.GAMMA_PRO),
                        gammaProService.connectionStatus()
                )
        );
        model.addAttribute("gammaProStatus", gammaProService.statusView());
        model.addAttribute("nicoleStatus", nicoleService.statusView());
        var shpSnapshot = shpService.snapshot();
        model.addAttribute(
                "shpStatus",
                shpStatusEvaluator.evaluate(
                        shpSnapshot.connected(),
                        shpSnapshot.measurements()
                )
        );
    }

    /**
     * Собирает устройства в порядке отображения на странице.
     *
     * @return новый изменяемый список видимых устройств
     */
    private List<Device> getVisibleDevices() {
        // Список камер может быть расширен, не затрагивая добавление остальных устройств.
        List<Device> devices = new ArrayList<>(cameraService.getCameras());
        devices.add(lenovoPcService.getLenovoPc());
        devices.add(nicoleService.getNicole());
        devices.add(bosonService.getBoson());
        devices.add(deviceViewService.getDevice(DeviceCode.ROBOTIC_MANIPULATOR));
        devices.add(deviceViewService.getDevice(DeviceCode.BARCODE_SCANNER));
        devices.add(deviceViewService.getDevice(DeviceCode.SCALES));
        devices.add(deviceViewService.getDevice(DeviceCode.SHP));
        return devices;
    }
}
