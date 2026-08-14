package com.rgs.center.gdrm;

import com.rgs.center.connection.SerialDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralCommunicationException;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.peripheral.PeripheralWorkerState;
import com.rgs.center.service.AbstractQueuedDeviceService;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Самостоятельно подключает и опрашивает Gamma Dose Rate Meter через COM-порт.
 *
 * <p>При идентификации сервис посылает по две команды чтения серийного номера
 * на скоростях 9600 и 115200, как это делает GDRMonitor. После ответа выбирается
 * формат данных 9, затем команды {@code d} и {@code t} выполняются циклически
 * через общие TX/RX-очереди {@link AbstractQueuedDeviceService}.</p>
 */
@Service
public class GdrmService extends AbstractQueuedDeviceService {

    private static final Logger log = LoggerFactory.getLogger(GdrmService.class);

    private static final int BAUD_RATE_9600 = 9600;
    private static final int BAUD_RATE_115200 = 115200;
    private static final int IDENTIFICATION_ATTEMPTS_PER_BAUD_RATE = 2;
    private static final int FAILURES_BEFORE_RECONNECT = 3;

    /** Настройки адреса, порта и тайм-аутов. */
    private final SettingsService settingsService;

    /** Общая витрина последнего состояния оборудования. */
    private final CurrentDataService currentDataService;

    /** COM-транспорт, скорость которого меняется при идентификации. */
    private final SerialDeviceConnection connection;

    /** Максимальное ожидание одного ответа прибора. */
    private final Duration responseTimeout;

    /** Не позволяет ручному и плановому опросу выполняться одновременно. */
    private final ReentrantLock pollLock = new ReentrantLock();

    private final AtomicBoolean identified = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<GdrmMeasurement> lastMeasurement =
            new AtomicReference<>();
    private final AtomicReference<GdrmOperatingData> lastOperatingData =
            new AtomicReference<>();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");

    /** Полученный из прибора адрес; до идентификации используется broadcast X. */
    private volatile String deviceAddress = GdrmProtocol.BROADCAST_ADDRESS;

    /** Последний прочитанный серийный номер. */
    private volatile String serialNumber = "";

    /** Последняя прочитанная версия встроенного ПО. */
    private volatile String firmwareVersion = "";

    /**
     * Создаёт сервис с COM-параметрами из {@code application.properties}.
     *
     * @param settingsService сервис настроек
     * @param currentDataService витрина текущих данных
     */
    @Autowired
    public GdrmService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this(
                settingsService,
                currentDataService,
                createConnection(settingsService)
        );
    }

    /** Внутренний конструктор оставляет транспорт явной зависимостью для тестов. */
    GdrmService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            SerialDeviceConnection connection
    ) {
        super(
                DeviceCode.GDRM,
                connection,
                PeripheralWorkerConfiguration.lineDevice(1024)
        );
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
        this.connection = connection;
        responseTimeout = Duration.ofMillis(Math.toIntExact(
                settingsService.getValue(
                        SettingsService.GDRM_RESPONSE_TIMEOUT_MILLIS,
                        Long.class
                )
        ));
    }

    /**
     * Выполняет независимый цикл идентификации или чтения данных прибора.
     */
    @Scheduled(
            fixedDelayString = "${rgs.device.gdrm.poll-interval-millis:1000}",
            initialDelayString = "${rgs.device.gdrm.poll-initial-delay-millis:1000}"
    )
    public void poll() {
        if (!isEnabled()) {
            return;
        }
        if (!pollLock.tryLock()) {
            log.debug("GDRM poll skipped because previous cycle is still running");
            return;
        }
        try {
            boolean wasIdentified = identified.get();
            if (!wasIdentified) {
                identifyDevice();
            } else {
                readCurrentData();
            }
            int recoveredAfterFailures = consecutiveFailures.getAndSet(0);
            lastError.set("");
            currentDataService.setValue(
                    DeviceCode.GDRM,
                    CurrentDataService.PINGED,
                    true
            );
            if (wasIdentified && recoveredAfterFailures > 0) {
                log.info(
                        "GDRM communication restored after {} failed poll(s)",
                        recoveredAfterFailures
                );
            }
        } catch (RuntimeException exception) {
            registerFailure(exception);
        } finally {
            pollLock.unlock();
        }
    }

    /**
     * Возвращает данные для отдельного HTTP endpoint без непосредственного
     * обращения к COM-порту из web-потока.
     *
     * @return неизменяемый снимок GDRM
     */
    public GdrmSnapshot snapshot() {
        long interactionIn = currentDataService.getValue(
                DeviceCode.GDRM,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        );
        long interactionOut = currentDataService.getValue(
                DeviceCode.GDRM,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Long.class
        );
        var communication = communicationSnapshot();
        boolean connected = identified.get()
                && communication.state() == PeripheralWorkerState.RUNNING;
        return new GdrmSnapshot(
                DeviceCode.GDRM.getCode(),
                connection.portName(),
                connected,
                connection.baudRate(),
                deviceAddress,
                serialNumber,
                firmwareVersion,
                interactionIn,
                interactionOut,
                lastError.get(),
                lastMeasurement.get(),
                lastOperatingData.get(),
                communication
        );
    }

    /**
     * Определяет скорость, читает серийный номер и включает формат данных 9.
     */
    private void identifyDevice() {
        RuntimeException lastFailure = null;
        for (int baudRate : candidateBaudRates()) {
            switchBaudRate(baudRate);
            for (int attempt = 1;
                    attempt <= IDENTIFICATION_ATTEMPTS_PER_BAUD_RATE;
                    attempt++) {
                try {
                    byte[] response = request(
                            GdrmProtocol.BROADCAST_ADDRESS,
                            "ss"
                    );
                    String value = GdrmProtocol.parseParameter(response, 's');
                    if (value.isBlank()) {
                        throw new PeripheralCommunicationException(
                                "GDRM returned an empty serial number"
                        );
                    }
                    serialNumber = value;
                    configureIdentifiedDevice();
                    identified.set(true);
                    currentDataService.recordDataSuccess(
                            DeviceCode.GDRM,
                            "serial=" + serialNumber
                    );
                    log.info(
                            "GDRM identified on {} at {} baud: serial={}, address={}, firmware={}",
                            connection.portName(),
                            baudRate,
                            serialNumber,
                            deviceAddress,
                            firmwareVersion
                    );
                    return;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    log.debug(
                            "GDRM identification failed on {} at {} baud, attempt {}/{}: {}",
                            connection.portName(),
                            baudRate,
                            attempt,
                            IDENTIFICATION_ATTEMPTS_PER_BAUD_RATE,
                            exception.getMessage()
                    );
                }
            }
        }
        throw new PeripheralCommunicationException(
                "GDRM did not answer identification on COM port "
                        + connection.portName(),
                lastFailure
        );
    }

    /**
     * Читает адрес, включает формат 9 и по возможности получает версию firmware.
     */
    private void configureIdentifiedDevice() {
        String address = GdrmProtocol.parseParameter(
                request(GdrmProtocol.BROADCAST_ADDRESS, "sa"),
                'a'
        );
        if (address.length() == 1) {
            deviceAddress = address;
        }

        String acknowledgement = GdrmProtocol.decode(
                request(deviceAddress, "c=9")
        );
        if (!"o.k.".equalsIgnoreCase(acknowledgement)) {
            throw new PeripheralCommunicationException(
                    "GDRM rejected format 9: " + acknowledgement
            );
        }

        try {
            firmwareVersion = GdrmProtocol.parseParameter(
                    request(deviceAddress, "sv"),
                    'v'
            );
        } catch (RuntimeException exception) {
            firmwareVersion = "";
            log.warn("Cannot read GDRM firmware version", exception);
        }
    }

    /** Выполняет один рабочий цикл команд {@code d} и {@code t}. */
    private void readCurrentData() {
        GdrmMeasurement measurement = GdrmProtocol.parseMeasurement(
                request(deviceAddress, "d")
        );
        GdrmOperatingData operatingData = GdrmProtocol.parseOperatingData(
                request(deviceAddress, "t")
        );
        lastMeasurement.set(measurement);
        lastOperatingData.set(operatingData);
        currentDataService.recordDataSuccess(DeviceCode.GDRM, measurement);
        log.debug(
                "GDRM data updated: doseRate={}, dose={}, status={}",
                measurement.weightedDoseRateSvPerHour(),
                measurement.accumulatedDoseSv(),
                measurement.deviceStatus()
        );
    }

    /**
     * Выполняет один последовательный обмен через общие TX/RX-очереди.
     */
    private byte[] request(String address, String body) {
        currentDataService.setValue(
                DeviceCode.GDRM,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );
        byte[] response = exchangeQueued(
                GdrmProtocol.command(address, body),
                responseTimeout
        );
        currentDataService.setValue(
                DeviceCode.GDRM,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Instant.now().getEpochSecond()
        );
        return response;
    }

    /** Останавливает worker, меняет baud rate и запускает чистый сеанс. */
    private void switchBaudRate(int baudRate) {
        stopCommunication();
        connection.setBaudRate(baudRate);
        startCommunication();
    }

    /** Формирует порядок скоростей: настройка пользователя, затем 9600/115200. */
    private int[] candidateBaudRates() {
        Set<Integer> rates = new LinkedHashSet<>();
        rates.add(Math.toIntExact(settingsService.getValue(
                SettingsService.GDRM_INITIAL_BAUD_RATE,
                Long.class
        )));
        rates.add(BAUD_RATE_9600);
        rates.add(BAUD_RATE_115200);
        return rates.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Фиксирует ошибку и после серии сбоев переводит сервис к переидентификации. */
    private void registerFailure(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        lastError.set(message);
        currentDataService.setValue(
                DeviceCode.GDRM,
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(DeviceCode.GDRM, message);

        int failures = consecutiveFailures.incrementAndGet();
        if (failures == FAILURES_BEFORE_RECONNECT) {
            identified.set(false);
            serialNumber = "";
            firmwareVersion = "";
            deviceAddress = GdrmProtocol.BROADCAST_ADDRESS;
            stopCommunication();
            log.error(
                    "GDRM communication failed {} times; reconnecting: {}",
                    failures,
                    message,
                    exception
            );
        } else if (failures == 1) {
            log.warn("GDRM communication failed: {}", message);
        } else {
            log.debug(
                    "GDRM communication still unavailable, failureCount={}: {}",
                    failures,
                    message
            );
        }
    }

    /** Создаёт jSerialComm-транспорт с параметрами GDRM. */
    private static SerialDeviceConnection createConnection(
            SettingsService settingsService
    ) {
        String portName = settingsService.getValue(
                SettingsService.GDRM_PORT,
                String.class
        );
        if (portName.isBlank()) {
            throw new IllegalArgumentException("GDRM COM port is not configured");
        }
        int baudRate = Math.toIntExact(settingsService.getValue(
                SettingsService.GDRM_INITIAL_BAUD_RATE,
                Long.class
        ));
        int readTimeout = Math.toIntExact(settingsService.getValue(
                SettingsService.GDRM_READ_TIMEOUT_MILLIS,
                Long.class
        ));
        return new SerialDeviceConnection(portName, baudRate, readTimeout);
    }
}
