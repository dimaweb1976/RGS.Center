package com.rgs.center.shp;

import com.rgs.center.connection.SerialDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.DelimitedFrameDecoder;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Самостоятельно подключает и опрашивает предусилитель сигнала SHP через
 * виртуальный COM-порт.
 *
 * <p>Команды проходят через общую ограниченную TX-очередь, ответы принимаются
 * отдельным RX-потоком и выделяются по маркерам {@code C0/0C}. Сервис не
 * обращается к порту из web-потока и публикует только готовый снимок.</p>
 */
@Service
public class ShpService extends AbstractQueuedDeviceService {

    private static final Logger log = LoggerFactory.getLogger(ShpService.class);
    private static final int BAUD_RATE = 9600;
    private static final int MAX_TRANSPORT_FRAME_LENGTH = 64;
    private static final int FAILURES_BEFORE_RECONNECT = 3;

    private final CurrentDataService currentDataService;
    private final SettingsService settingsService;
    private final SerialDeviceConnection connection;
    private final ReentrantLock pollLock = new ReentrantLock();
    private final AtomicBoolean responsive = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<ShpMeasurements> lastMeasurements =
            new AtomicReference<>();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");

    /**
     * Создаёт SHP-сервис с настройками COM-порта из application.properties.
     *
     * @param settingsService сервис настроек
     * @param currentDataService витрина текущих данных
     */
    @Autowired
    public ShpService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this(
                settingsService,
                currentDataService,
                createConnection(settingsService)
        );
    }

    /** Внутренний конструктор оставляет COM-транспорт явной зависимостью. */
    ShpService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            SerialDeviceConnection connection
    ) {
        super(
                DeviceCode.SHP,
                connection,
                new PeripheralWorkerConfiguration(
                        1024,
                        32,
                        32,
                        64,
                        MAX_TRANSPORT_FRAME_LENGTH,
                        true
                ),
                new DelimitedFrameDecoder(
                        ShpProtocol.START,
                        ShpProtocol.STOP,
                        MAX_TRANSPORT_FRAME_LENGTH
                )
        );
        this.currentDataService = currentDataService;
        this.settingsService = settingsService;
        this.connection = connection;
    }

    /** Выполняет один полный цикл чтения параметров SHP. */
    @Scheduled(
            fixedDelayString = "${rgs.device.shp.poll-interval-millis:2000}",
            initialDelayString = "${rgs.device.shp.poll-initial-delay-millis:2000}"
    )
    public void poll() {
        if (!isEnabled()) {
            return;
        }
        if (!pollLock.tryLock()) {
            log.debug("SHP poll skipped because previous cycle is still running");
            return;
        }
        try {
            ShpMeasurements measurements = new ShpMeasurements(
                    readSensor(1),
                    readSensor(2),
                    readSensor(3),
                    readSensor(4),
                    readSensor(5),
                    readSensor(6),
                    readMeteo(1),
                    readMeteo(2),
                    readMeteo(3),
                    Instant.now().getEpochSecond()
            );
            lastMeasurements.set(measurements);
            responsive.set(true);
            int recoveredAfterFailures = consecutiveFailures.getAndSet(0);
            lastError.set("");
            currentDataService.setValue(
                    DeviceCode.SHP,
                    CurrentDataService.PINGED,
                    true
            );
            currentDataService.recordDataSuccess(DeviceCode.SHP, measurements);
            if (recoveredAfterFailures > 0) {
                log.info(
                        "SHP communication restored after {} failed poll(s)",
                        recoveredAfterFailures
                );
            }
            log.debug(
                    "SHP data updated: ambientTemperature={}, humidity={}, pressure={}, leakageCurrent={}",
                    measurements.ambientTemperatureCelsius(),
                    measurements.humidityPercent(),
                    measurements.atmosphericPressureHpa(),
                    measurements.detectorCurrent()
            );
        } catch (RuntimeException exception) {
            registerFailure(exception);
        } finally {
            pollLock.unlock();
        }
    }

    /**
     * Возвращает последний готовый снимок без синхронного чтения COM-порта.
     *
     * @return состояние SHP и его фонового worker
     */
    public ShpSnapshot snapshot() {
        var communication = communicationSnapshot();
        return new ShpSnapshot(
                DeviceCode.SHP.getCode(),
                connection.portName(),
                responsive.get()
                        && communication.state() == PeripheralWorkerState.RUNNING,
                connection.baudRate(),
                currentDataService.getValue(
                        DeviceCode.SHP,
                        CurrentDataService.INTERACTION_LASTDATE_IN,
                        Long.class
                ),
                currentDataService.getValue(
                        DeviceCode.SHP,
                        CurrentDataService.INTERACTION_LASTDATE_OUT,
                        Long.class
                ),
                lastError.get(),
                lastMeasurements.get(),
                communication
        );
    }

    /** Читает один параметр команды SENSORS_SHP. */
    private double readSensor(int parameter) {
        return ShpProtocol.parseSensorValue(
                request(ShpProtocol.readSensorCommand(parameter)),
                parameter
        );
    }

    /** Читает температуру, влажность или давление командой METEO_SHP. */
    private double readMeteo(int parameter) {
        return ShpProtocol.parseMeteoValue(
                request(ShpProtocol.readMeteoCommand(parameter)),
                parameter
        );
    }

    /** Выполняет один обмен через общие TX/RX-очереди и фиксирует время. */
    private byte[] request(byte[] command) {
        currentDataService.setValue(
                DeviceCode.SHP,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );
        byte[] response = exchangeQueued(command, responseTimeout());
        currentDataService.setValue(
                DeviceCode.SHP,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Instant.now().getEpochSecond()
        );
        return response;
    }

    /** Фиксирует ошибку и после серии сбоев закрывает COM-порт для переподключения. */
    private void registerFailure(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        responsive.set(false);
        lastError.set(message);
        currentDataService.setValue(
                DeviceCode.SHP,
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(DeviceCode.SHP, message);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures == FAILURES_BEFORE_RECONNECT) {
            stopCommunication();
            log.error(
                    "SHP communication failed {} times; reconnecting: {}",
                    failures,
                    message,
                    exception
            );
        } else if (failures == 1) {
            log.warn("SHP communication failed: {}", message);
        } else {
            log.debug(
                    "SHP communication still unavailable, failureCount={}: {}",
                    failures,
                    message
            );
        }
    }

    /** Создаёт COM5/9600 8N1 и устанавливает DTR=0, как в HPGe.Center. */
    private static SerialDeviceConnection createConnection(
            SettingsService settingsService
    ) {
        return new SerialDeviceConnection(
                "serial:shp",
                () -> settingsService.getValue(
                        SettingsService.SHP_PORT,
                        String.class
                ),
                () -> settingInt(
                        settingsService,
                        SettingsService.SHP_BAUD_RATE
                ),
                () -> settingInt(
                        settingsService,
                        SettingsService.SHP_READ_TIMEOUT_MILLIS
                ),
                false
        );
    }

    private Duration responseTimeout() {
        return Duration.ofMillis(settingInt(
                settingsService,
                SettingsService.SHP_RESPONSE_TIMEOUT_MILLIS
        ));
    }

    private static int settingInt(
            SettingsService settingsService,
            String settingCode
    ) {
        return Math.toIntExact(settingsService.getValue(
                settingCode,
                Long.class
        ));
    }
}
