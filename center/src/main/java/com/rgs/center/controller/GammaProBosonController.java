package com.rgs.center.controller;

import com.rgs.center.gammapro.GammaProConnectionStatus;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Сервисная HTML-страница ручного управления связкой Gamma-PRO/BOSON.
 *
 * <p>Контроллер не обращается к USB-B напрямую. Все команды проходят через
 * единственное TCP-соединение с Gamma-PRO, а состояние процесса BOSON берётся
 * из свежего ответа GET_TRACTS_NAME. Значение HV на странице является только
 * параметром отправленной команды: чтение фактического напряжения протоколом
 * проекта не поддерживается.</p>
 */
@Controller
@RequestMapping("/gammapro-boson")
public class GammaProBosonController {

    private static final Logger log = LoggerFactory.getLogger(
            GammaProBosonController.class
    );

    private final GammaProService gammaProService;
    private final BosonService bosonService;
    private final SettingsService settingsService;

    /**
     * @param gammaProService состояние сокета и протокола Gamma-PRO
     * @param bosonService безопасные операции над выбранным трактом BOSON
     * @param settingsService настроенные имена анализатора и канала
     */
    public GammaProBosonController(
            GammaProService gammaProService,
            BosonService bosonService,
            SettingsService settingsService
    ) {
        this.gammaProService = gammaProService;
        this.bosonService = bosonService;
        this.settingsService = settingsService;
    }

    /** @return полная страница управления и мониторинга */
    @GetMapping
    public String page(Model model) {
        addStatus(model);
        return "gammapro-boson";
    }

    /** @return фрагмент, который браузер обновляет раз в десять секунд */
    @GetMapping("/status")
    public String status(Model model) {
        addStatus(model);
        return "fragments/gammapro-boson-status :: status";
    }

    /** Выбирает BOSON/Ch1, не изменяя состояние высокого напряжения. */
    @PostMapping("/tract")
    public String selectTract(Model model) {
        return execute(
                "SET_TRACT accepted by Gamma-PRO",
                () -> bosonService.selectTract(mcaName(), channelName()),
                model
        );
    }

    /**
     * Повторно выбирает тракт и запрашивает SET_HV(1,1000,0).
     * Успешный ответ означает принятие команды, но не измерение реального HV.
     */
    @PostMapping("/hv/on")
    public String highVoltageOn(Model model) {
        return execute(
                "SET_HV(1,1000,0) accepted by Gamma-PRO",
                () -> bosonService.enableHighVoltage(
                        mcaName(),
                        channelName(),
                        BosonService.MANUAL_HIGH_VOLTAGE
                ),
                model
        );
    }

    /**
     * Повторно выбирает тракт и запрашивает SET_HV(0,1000,0).
     * Параметр 1000 сохранён в точном соответствии с утверждённым ТЗ.
     */
    @PostMapping("/hv/off")
    public String highVoltageOff(Model model) {
        return execute(
                "SET_HV(0,1000,0) accepted by Gamma-PRO",
                () -> bosonService.disableHighVoltage(
                        mcaName(),
                        channelName()
                ),
                model
        );
    }

    /**
     * Преобразует протокольную ошибку в обычный HTML-фрагмент. Благодаря этому
     * ER, тайм-аут или отсутствующий сокет видны оператору и не превращаются в
     * необработанный HTTP 500.
     */
    private String execute(
            String successMessage,
            Command command,
            Model model
    ) {
        try {
            command.run();
            model.addAttribute("actionMessage", successMessage);
            model.addAttribute("actionError", false);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("GammaPro_BOSON manual action failed: {}", message);
            model.addAttribute("actionMessage", message);
            model.addAttribute("actionError", true);
        }
        return status(model);
    }

    /** Заполняет одну и ту же модель для полной страницы и AJAX-фрагмента. */
    private void addStatus(Model model) {
        var status = gammaProService.statusView();
        boolean controlsEnabled = status.connectionStatus()
                == GammaProConnectionStatus.CONNECTED
                && status.snapshot().measurementState()
                != GammaProMeasurementState.RUNNING;
        model.addAttribute("gammaProBosonStatus", status);
        model.addAttribute(
                "bosonHighVoltage",
                gammaProService.bosonHighVoltageSnapshot()
        );
        model.addAttribute("bosonMcaName", mcaName());
        model.addAttribute("bosonChannelName", channelName());
        model.addAttribute(
                "bosonManualHighVoltage",
                BosonService.MANUAL_HIGH_VOLTAGE
        );
        model.addAttribute("gammaProBosonControlsEnabled", controlsEnabled);
    }

    private String mcaName() {
        return settingsService.getValue(
                SettingsService.BOSON_MCA_NAME,
                String.class
        );
    }

    private String channelName() {
        return settingsService.getValue(
                SettingsService.BOSON_CHANNEL_NAME,
                String.class
        );
    }

    /** Команда без аргументов, выполняемая внутри общего обработчика ошибок. */
    @FunctionalInterface
    private interface Command {
        void run();
    }
}
