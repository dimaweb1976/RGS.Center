package com.rgs.center.measurement.emulator;

import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.camera.DetectedBottle;
import com.rgs.center.nicole.NicoleReadinessStatus;
import com.rgs.center.nicole.NicoleTechnicalStatusChangedEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Потокобезопасный эмулятор верхнеуровневого алгоритма измерения.
 *
 * <p>Сервис не обращается к физическим портам. Он моделирует проверки,
 * параллельную подготовку и последовательное тело цикла, чтобы web-интерфейс
 * и будущий оркестратор можно было разрабатывать без стенда.</p>
 */
@Service
public class MeasurementEmulatorService {

    private static final Logger log =
            LoggerFactory.getLogger(MeasurementEmulatorService.class);
    private static final DateTimeFormatter EVENT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_EVENTS = 30;
    private static final double LEAKAGE_LIMIT_MICROAMPS = 1.0;

    /** Сервис получения кадра, распознавания и сохранения изображений. */
    private final CameraImagingOperations cameraImaging;

    /** Точное тело цикла из требований; каждый шаг включает ожидание завершения. */
    private static final List<String> CYCLE_STEP_NAMES = List.of(
            "PLC: взять объект по координатам X,Y и дождаться завершения",
            "PLC: отнести объект на точку чтения кода и дождаться завершения",
            "Сканер: прочитать, валидировать и занести штрих-код в таблицу",
            "PLC: отнести объект на весы и дождаться завершения",
            "Весы: прочитать, валидировать и занести вес в таблицу",
            "PLC: отнести объект в спектрометр и дождаться завершения",
            "Gamma-PRO: набрать спектр и дождаться завершения",
            "PLC: забрать объект из спектрометра и дождаться завершения",
            "PLC: вернуть объект по координатам X,Y и дождаться завершения"
    );

    private final Map<String, CheckState> deviceChecks = new LinkedHashMap<>();
    private final Map<String, CheckState> initialStateChecks = new LinkedHashMap<>();
    private final Map<String, CheckState> controlParameterChecks = new LinkedHashMap<>();
    private final List<MutableObject> objects = new ArrayList<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();

    private MeasurementEmulatorPhase phase;
    private String alert;
    private String highVoltageState;
    private double leakageCurrentMicroamps;
    private int preparationTicks;
    private int activeObjectIndex;
    private int activeStepIndex;
    private int completedObjects;

    public MeasurementEmulatorService(CameraImagingOperations cameraImaging) {
        this.cameraImaging = cameraImaging;
    }

    /** Формирует исходное состояние после создания Spring-компонента. */
    @PostConstruct
    public synchronized void reset() {
        deviceChecks.clear();
        addCheck(deviceChecks, "camera", "Камера", "Связь установлена");
        addCheck(deviceChecks, "lenovo-pc", "LenovoPC", "LAN-связь установлена");
        addCheck(deviceChecks, "manipulator", "Манипулятор Dobot", "TCP-связь установлена");
        addCheck(deviceChecks, "nicole", "Nicole", "Связь установлена");
        addCheck(deviceChecks, "boson", "BOSON", "Связь установлена");
        addCheck(deviceChecks, "barcode", "Сканер штрих-кодов", "COM-порт открыт");
        addCheck(deviceChecks, "scales", "Весы", "COM-порт открыт");
        addCheck(deviceChecks, "shp", "SHP", "COM-порт открыт");
        addCheck(deviceChecks, "gamma-pro", "Gamma-PRO", "TCP-сервер отвечает");

        initialStateChecks.clear();
        addCheck(initialStateChecks, "manipulator-open", "Манипулятор разжат", "Исходное положение");
        addCheck(initialStateChecks, "axis-home", "Оси в HOME", "Перемещение разрешено");
        addCheck(initialStateChecks, "door-closed", "Дверь закрыта", "Блокировка активна");
        addCheck(initialStateChecks, "hv-safe", "Высокое выключено", "Безопасное начальное состояние");

        controlParameterChecks.clear();
        addCheck(controlParameterChecks, "nicole-ok", "Nicole: OK", "Контрольный ответ получен");
        addCheck(controlParameterChecks, "boson-ready", "BOSON.Status = READY", "Источник готов");
        addCheck(controlParameterChecks, "shp-ok", "Сигнал SHP в норме", "Контроль детектора пройден");

        objects.clear();
        events.clear();
        cameraImaging.reset();
        phase = MeasurementEmulatorPhase.PRECHECK;
        alert = "";
        highVoltageState = "ВЫКЛЮЧЕНО";
        leakageCurrentMicroamps = 0.18;
        preparationTicks = 0;
        activeObjectIndex = -1;
        activeStepIndex = -1;
        completedObjects = 0;
        addEvent("Эмулятор сброшен. Запущены первичные проверки.");
        log.info("Measurement algorithm emulator reset");
    }

    /** Продвигает автомат состояний на один такт. */
    @Scheduled(
            fixedDelayString = "${rgs.measurement-emulator.tick-millis:1000}",
            initialDelayString = "${rgs.measurement-emulator.initial-delay-millis:1000}"
    )
    public synchronized void advance() {
        switch (phase) {
            case PRECHECK -> advancePrecheck();
            case PARALLEL_PREPARATION -> advancePreparation();
            case WAITING_FOR_OBJECTS -> updateReadyState();
            case READY_TO_START -> verifyReadyState();
            case RUNNING -> advanceCycle();
            case COMPLETED, ALERT -> {
                // Конечные состояния изменяются явной командой оператора.
            }
        }
    }

    /** Загружает тестовый кадр или получает новый снимок реальной камеры. */
    public synchronized void captureCameraImage() {
        if (phase == MeasurementEmulatorPhase.RUNNING) {
            addEvent("Снимок камеры запрещён во время выполнения цикла.");
            return;
        }
        try {
            CameraImagingSnapshot camera = cameraImaging.capture();
            objects.clear();
            addEvent(camera.message());
            updateReadyState();
        } catch (RuntimeException exception) {
            addEvent("Ошибка получения снимка: " + errorMessage(exception) + ".");
        }
    }

    /** Распознаёт бутылки на последнем снимке и формирует список объектов. */
    public synchronized void recognizeBottles() {
        if (phase == MeasurementEmulatorPhase.RUNNING) {
            addEvent("Распознавание запрещено во время выполнения цикла.");
            return;
        }
        try {
            CameraImagingSnapshot camera = cameraImaging.recognize();
            objects.clear();
            for (DetectedBottle bottle : camera.bottles()) {
                objects.add(new MutableObject(
                        bottle.number(),
                        "НЕ СЧИТАН",
                        0.0,
                        "ОБНАРУЖЕН"
                ));
            }
            addEvent(camera.message());
            addEvent("Оригинал сохранён: " + camera.originalFile() + ".");
            addEvent("Размеченный снимок сохранён: " + camera.detectedFile() + ".");
            updateReadyState();
        } catch (RuntimeException exception) {
            addEvent("Ошибка распознавания: " + errorMessage(exception) + ".");
        }
    }

    /** Совместимость со старым именем операции определения объектов. */
    public synchronized void detectObjects() {
        recognizeBottles();
    }

    private static String errorMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    /** Запускает обработку списка, только если все условия истинны. */
    public synchronized void startCycle() {
        if (!canStart()) {
            enterAlert("START запрещён: оборудование или список объектов не готовы");
            return;
        }
        phase = MeasurementEmulatorPhase.RUNNING;
        activeObjectIndex = 0;
        activeStepIndex = 0;
        objects.get(0).status = "В РАБОТЕ";
        addEvent("START: начат цикл по списку объектов.");
        addEvent("Объект 1: " + CYCLE_STEP_NAMES.get(0) + ".");
        log.info("Measurement emulator cycle started, objects={}", objects.size());
    }

    /** Переключает результат выбранной проверки для имитации отказа. */
    public synchronized void toggleCheck(String code) {
        CheckState check = findCheck(code);
        if (check == null) {
            throw new IllegalArgumentException("Unknown emulator check: " + code);
        }
        check.ready = !check.ready;
        check.details = check.ready ? "Эмуляция: норма" : "Эмуляция: отказ";
        addEvent(check.label + ": " + (check.ready ? "НОРМА" : "ОТКАЗ") + ".");
        if (!check.ready) {
            enterAlert("Не пройдена проверка: " + check.label);
        } else if (phase == MeasurementEmulatorPhase.ALERT && allChecksReady()
                && leakageCurrentMicroamps <= LEAKAGE_LIMIT_MICROAMPS) {
            recoverFromAlert("Причина оповещения устранена");
        }
    }

    /** Переключает ток утечки между нормальным и аварийным значением. */
    public synchronized void toggleLeakageCurrent() {
        leakageCurrentMicroamps = leakageCurrentMicroamps <= LEAKAGE_LIMIT_MICROAMPS
                ? 1.40
                : 0.18;
        addEvent("Ток утечки: " + leakageCurrentMicroamps + " мкА.");
        if (leakageCurrentMicroamps > LEAKAGE_LIMIT_MICROAMPS) {
            enterAlert("Превышен допустимый ток утечки");
        } else if (phase == MeasurementEmulatorPhase.ALERT && allChecksReady()) {
            recoverFromAlert("Ток утечки нормализован");
        }
    }

    /**
     * Принимает изменение технического состояния NICOLE из фонового сервиса.
     *
     * <p>Переход READY → ALERT во время цикла немедленно переводит автомат в
     * аварийное состояние. Повторные PONG со статусом ALERT нового события не
     * создают, а возврат READY только фиксируется: остановленный сценарий не
     * возобновляется автоматически.</p>
     *
     * @param event новое вычисленное состояние по PONG
     */
    @EventListener
    public synchronized void onNicoleTechnicalStatusChanged(
            NicoleTechnicalStatusChangedEvent event
    ) {
        CheckState check = controlParameterChecks.get("nicole-ok");
        if (check == null) {
            log.error("NICOLE control check is not initialized");
            return;
        }

        if (event.currentStatus() == NicoleReadinessStatus.READY) {
            check.ready = true;
            check.details = "Технический статус READY";
            addEvent("Nicole: технический статус READY.");
            return;
        }
        if (event.currentStatus() != NicoleReadinessStatus.ALERT) {
            return;
        }

        check.ready = false;
        String reason = event.reasons().isEmpty()
                ? "причина не указана"
                : String.join(", ", event.reasons());
        check.details = "ALERT: " + reason;
        if (event.isReadyToAlert()
                && phase == MeasurementEmulatorPhase.RUNNING) {
            enterAlert("Nicole: технический статус ALERT (" + reason + ")");
        } else {
            addEvent("Nicole: технический статус ALERT — " + reason + ".");
        }
    }

    /** @return согласованный снимок всех полей эмулятора */
    public synchronized MeasurementEmulatorSnapshot snapshot() {
        CameraImagingSnapshot camera = cameraImaging.snapshot();
        return new MeasurementEmulatorSnapshot(
                phase,
                phase.getDisplayName(),
                alert,
                canStart(),
                highVoltageState,
                leakageCurrentMicroamps,
                LEAKAGE_LIMIT_MICROAMPS,
                camera.imageAvailable(),
                camera.imageVersion(),
                camera.recognized(),
                camera.message(),
                camera.originalFile(),
                camera.detectedFile(),
                camera.bottles(),
                activeObjectIndex < 0 ? 0 : activeObjectIndex + 1,
                completedObjects,
                checksSnapshot(deviceChecks),
                checksSnapshot(initialStateChecks),
                checksSnapshot(controlParameterChecks),
                objectsSnapshot(),
                stepsSnapshot(),
                List.copyOf(events)
        );
    }

    private void advancePrecheck() {
        if (!allChecksReady()) {
            enterAlert("Одна или несколько первичных проверок завершились ошибкой");
            return;
        }
        phase = MeasurementEmulatorPhase.PARALLEL_PREPARATION;
        highVoltageState = "НАБОР НАПРЯЖЕНИЯ";
        preparationTicks = 0;
        addEvent("Параллельно запущен набор высокого; окно камеры готово к снимку.");
    }

    private void advancePreparation() {
        if (!allChecksReady()) {
            enterAlert("Оборудование потеряло готовность во время подготовки");
            return;
        }
        preparationTicks++;
        if (preparationTicks < 2) {
            return;
        }
        highVoltageState = "ВЫСТАВЛЕНО";
        if (leakageCurrentMicroamps > LEAKAGE_LIMIT_MICROAMPS) {
            enterAlert("Превышен допустимый ток утечки");
            return;
        }
        phase = MeasurementEmulatorPhase.WAITING_FOR_OBJECTS;
        addEvent("Высокое выставлено; ток утечки в норме.");
        updateReadyState();
    }

    private void updateReadyState() {
        if (phase == MeasurementEmulatorPhase.RUNNING
                || phase == MeasurementEmulatorPhase.COMPLETED
                || phase == MeasurementEmulatorPhase.ALERT) {
            return;
        }
        if (highVoltageState.equals("ВЫСТАВЛЕНО")
                && !objects.isEmpty()
                && allChecksReady()
                && leakageCurrentMicroamps <= LEAKAGE_LIMIT_MICROAMPS) {
            if (phase != MeasurementEmulatorPhase.READY_TO_START) {
                addEvent("Все условия выполнены. Кнопка START доступна.");
            }
            phase = MeasurementEmulatorPhase.READY_TO_START;
        } else if (highVoltageState.equals("ВЫСТАВЛЕНО")) {
            phase = MeasurementEmulatorPhase.WAITING_FOR_OBJECTS;
        }
    }

    private void verifyReadyState() {
        if (!allChecksReady()) {
            enterAlert("Оборудование потеряло готовность перед START");
        } else if (leakageCurrentMicroamps > LEAKAGE_LIMIT_MICROAMPS) {
            enterAlert("Превышен допустимый ток утечки");
        } else if (objects.isEmpty()) {
            phase = MeasurementEmulatorPhase.WAITING_FOR_OBJECTS;
        }
    }

    private void advanceCycle() {
        if (!allChecksReady()) {
            enterAlert("Оборудование потеряло готовность во время цикла");
            return;
        }
        if (leakageCurrentMicroamps > LEAKAGE_LIMIT_MICROAMPS) {
            enterAlert("Превышен допустимый ток утечки во время цикла");
            return;
        }
        activeStepIndex++;
        if (activeStepIndex < CYCLE_STEP_NAMES.size()) {
            addEvent("Объект " + (activeObjectIndex + 1) + ": "
                    + CYCLE_STEP_NAMES.get(activeStepIndex) + ".");
            return;
        }
        MutableObject completed = objects.get(activeObjectIndex);
        completed.status = "ГОТОВ";
        completedObjects++;
        addEvent("Объект " + completed.number + " полностью обработан.");
        activeObjectIndex++;
        if (activeObjectIndex >= objects.size()) {
            phase = MeasurementEmulatorPhase.COMPLETED;
            activeObjectIndex = -1;
            activeStepIndex = CYCLE_STEP_NAMES.size();
            addEvent("Цикл завершён: обработано объектов — " + completedObjects + ".");
            log.info("Measurement emulator cycle completed");
            return;
        }
        activeStepIndex = 0;
        objects.get(activeObjectIndex).status = "В РАБОТЕ";
        addEvent("Объект " + (activeObjectIndex + 1) + ": "
                + CYCLE_STEP_NAMES.get(0) + ".");
    }

    private boolean canStart() {
        return phase == MeasurementEmulatorPhase.READY_TO_START
                && allChecksReady()
                && highVoltageState.equals("ВЫСТАВЛЕНО")
                && leakageCurrentMicroamps <= LEAKAGE_LIMIT_MICROAMPS
                && !objects.isEmpty();
    }

    private boolean allChecksReady() {
        return ready(deviceChecks) && ready(initialStateChecks)
                && ready(controlParameterChecks);
    }

    private static boolean ready(Map<String, CheckState> checks) {
        return checks.values().stream().allMatch(check -> check.ready);
    }

    private void enterAlert(String message) {
        phase = MeasurementEmulatorPhase.ALERT;
        alert = message;
        highVoltageState = "АВАРИЙНО ВЫКЛЮЧЕНО";
        addEvent("ОПОВЕЩЕНИЕ: " + message + ".");
        log.warn("Measurement emulator alert: {}", message);
    }

    private void recoverFromAlert(String reason) {
        phase = MeasurementEmulatorPhase.PRECHECK;
        alert = "";
        highVoltageState = "ВЫКЛЮЧЕНО";
        preparationTicks = 0;
        addEvent(reason + ". Проверки запущены повторно.");
    }

    private CheckState findCheck(String code) {
        CheckState state = deviceChecks.get(code);
        if (state == null) state = initialStateChecks.get(code);
        if (state == null) state = controlParameterChecks.get(code);
        return state;
    }

    private void addEvent(String message) {
        while (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(LocalTime.now().format(EVENT_TIME) + " — " + message);
    }

    private static void addCheck(
            Map<String, CheckState> destination,
            String code,
            String label,
            String details
    ) {
        destination.put(code, new CheckState(code, label, details));
    }

    private static List<EmulatorCheck> checksSnapshot(Map<String, CheckState> source) {
        return source.values().stream()
                .map(check -> new EmulatorCheck(
                        check.code, check.label, check.ready, check.details
                ))
                .toList();
    }

    private List<EmulatedMeasurementObject> objectsSnapshot() {
        return objects.stream()
                .map(object -> new EmulatedMeasurementObject(
                        object.number,
                        object.barcode,
                        object.estimatedMassGrams,
                        object.status
                ))
                .toList();
    }

    private List<EmulatorCycleStep> stepsSnapshot() {
        List<EmulatorCycleStep> result = new ArrayList<>();
        for (int index = 0; index < CYCLE_STEP_NAMES.size(); index++) {
            String state = "ОЖИДАЕТ";
            if (phase == MeasurementEmulatorPhase.COMPLETED
                    || (phase == MeasurementEmulatorPhase.RUNNING
                    && index < activeStepIndex)) {
                state = "ГОТОВО";
            } else if (phase == MeasurementEmulatorPhase.RUNNING
                    && index == activeStepIndex) {
                state = "ВЫПОЛНЯЕТСЯ";
            }
            result.add(new EmulatorCycleStep(index + 1, CYCLE_STEP_NAMES.get(index), state));
        }
        return List.copyOf(result);
    }

    private static final class CheckState {
        private final String code;
        private final String label;
        private boolean ready = true;
        private String details;

        private CheckState(String code, String label, String details) {
            this.code = code;
            this.label = label;
            this.details = details;
        }
    }

    private static final class MutableObject {
        private final int number;
        private final String barcode;
        private final double estimatedMassGrams;
        private String status = "ОЖИДАЕТ";

        private MutableObject(int number, String barcode, double estimatedMassGrams) {
            this(number, barcode, estimatedMassGrams, "ОЖИДАЕТ");
        }

        private MutableObject(
                int number,
                String barcode,
                double estimatedMassGrams,
                String status
        ) {
            this.number = number;
            this.barcode = barcode;
            this.estimatedMassGrams = estimatedMassGrams;
            this.status = status;
        }
    }
}
