package com.rgs.center.scales;

import com.rgs.center.connection.SerialDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralTransport;
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

/** Background request/response polling of RADWAG WTC scales over a COM port. */
@Service
public class ScalesService extends AbstractQueuedDeviceService {

    private static final Logger log = LoggerFactory.getLogger(ScalesService.class);
    private static final int MAX_FRAME_LENGTH = 32;
    private static final int FAILURES_BEFORE_RECONNECT = 3;

    private final CurrentDataService currentDataService;
    private final SettingsService settingsService;
    private final String portName;
    private final int baudRate;
    private final Duration responseTimeout;
    private final ReentrantLock pollLock = new ReentrantLock();
    private final AtomicBoolean responsive = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<ScalesReading> lastReading =
            new AtomicReference<>();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");

    /** Creates the production COM transport from application settings. */
    @Autowired
    public ScalesService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this(
                settingsService,
                currentDataService,
                serialConfiguration(settingsService),
                responseTimeout(settingsService)
        );
    }

    private ScalesService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            SerialConfiguration serial,
            Duration responseTimeout
    ) {
        this(
                currentDataService,
                new SerialDeviceConnection(
                        "serial:scales",
                        () -> settingsService.getValue(
                                SettingsService.SCALES_PORT,
                                String.class
                        ),
                        () -> settingInt(
                                settingsService,
                                SettingsService.SCALES_BAUD_RATE
                        ),
                        () -> settingInt(
                                settingsService,
                                SettingsService.SCALES_READ_TIMEOUT_MILLIS
                        )
                ),
                serial.portName(),
                serial.baudRate(),
                responseTimeout,
                settingsService
        );
    }

    /** Package-visible constructor keeps the transport replaceable in tests. */
    ScalesService(
            CurrentDataService currentDataService,
            PeripheralTransport transport,
            String portName,
            int baudRate,
            Duration responseTimeout
    ) {
        this(
                currentDataService,
                transport,
                portName,
                baudRate,
                responseTimeout,
                null
        );
    }

    private ScalesService(
            CurrentDataService currentDataService,
            PeripheralTransport transport,
            String portName,
            int baudRate,
            Duration responseTimeout,
            SettingsService settingsService
    ) {
        super(
                DeviceCode.SCALES,
                transport,
                PeripheralWorkerConfiguration.lineDevice(MAX_FRAME_LENGTH)
        );
        this.currentDataService = currentDataService;
        this.settingsService = settingsService;
        this.portName = portName;
        this.baudRate = baudRate;
        this.responseTimeout = responseTimeout;
    }

    /** Requests one immediate mass frame every configured interval. */
    @Scheduled(
            fixedDelayString = "${rgs.device.scales.poll-interval-millis:1000}",
            initialDelayString = "${rgs.device.scales.poll-initial-delay-millis:1000}"
    )
    public void poll() {
        if (!isEnabled()) {
            return;
        }
        if (!pollLock.tryLock()) {
            log.debug("Scales poll skipped because the previous cycle is running");
            return;
        }
        try {
            currentDataService.setValue(
                    DeviceCode.SCALES,
                    CurrentDataService.INTERACTION_LASTDATE_IN,
                    Instant.now().getEpochSecond()
            );
            byte[] frame = exchangeQueued(
                    ScalesProtocol.immediateMassCommand(),
                    configuredResponseTimeout()
            );
            currentDataService.setValue(
                    DeviceCode.SCALES,
                    CurrentDataService.INTERACTION_LASTDATE_OUT,
                    Instant.now().getEpochSecond()
            );
            ScalesReading reading = ScalesProtocol.parseImmediateMass(frame);
            lastReading.set(reading);
            responsive.set(true);
            int recoveredAfterFailures = consecutiveFailures.getAndSet(0);
            lastError.set("");
            currentDataService.setValue(
                    DeviceCode.SCALES,
                    CurrentDataService.PINGED,
                    true
            );
            currentDataService.recordDataSuccess(DeviceCode.SCALES, reading);
            if (recoveredAfterFailures > 0) {
                log.info(
                        "Scales communication restored after {} failed poll(s)",
                        recoveredAfterFailures
                );
            }
            log.debug(
                    "Scales data updated: mass={} {}, stability={}",
                    reading.mass(),
                    reading.unit(),
                    reading.stability()
            );
        } catch (RuntimeException exception) {
            registerFailure(exception);
        } finally {
            pollLock.unlock();
        }
    }

    /** Returns the last completed state without touching the COM port. */
    public ScalesSnapshot snapshot() {
        var communication = communicationSnapshot();
        return new ScalesSnapshot(
                DeviceCode.SCALES.getCode(),
                configuredPortName(),
                responsive.get()
                        && communication.state() == PeripheralWorkerState.RUNNING,
                configuredBaudRate(),
                currentDataService.getValue(
                        DeviceCode.SCALES,
                        CurrentDataService.INTERACTION_LASTDATE_IN,
                        Long.class
                ),
                currentDataService.getValue(
                        DeviceCode.SCALES,
                        CurrentDataService.INTERACTION_LASTDATE_OUT,
                        Long.class
                ),
                lastError.get(),
                lastReading.get(),
                communication
        );
    }

    private void registerFailure(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        responsive.set(false);
        lastError.set(message);
        currentDataService.setValue(
                DeviceCode.SCALES,
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(DeviceCode.SCALES, message);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURES_BEFORE_RECONNECT) {
            consecutiveFailures.set(0);
            stopCommunication();
            log.error(
                    "Scales communication failed {} times; reconnecting: {}",
                    failures,
                    message,
                    exception
            );
        } else if (failures == 1) {
            log.warn("Scales communication failed: {}", message);
        } else {
            log.debug(
                    "Scales communication still unavailable, failureCount={}: {}",
                    failures,
                    message
            );
        }
    }

    private static SerialConfiguration serialConfiguration(
            SettingsService settingsService
    ) {
        return new SerialConfiguration(
                settingsService.getValue(
                        SettingsService.SCALES_PORT,
                        String.class
                ),
                Math.toIntExact(settingsService.getValue(
                        SettingsService.SCALES_BAUD_RATE,
                        Long.class
                )),
                Math.toIntExact(settingsService.getValue(
                        SettingsService.SCALES_READ_TIMEOUT_MILLIS,
                        Long.class
                ))
        );
    }

    private static Duration responseTimeout(SettingsService settingsService) {
        return Duration.ofMillis(Math.toIntExact(settingsService.getValue(
                SettingsService.SCALES_RESPONSE_TIMEOUT_MILLIS,
                Long.class
        )));
    }

    private String configuredPortName() {
        return settingsService == null
                ? portName
                : settingsService.getValue(
                        SettingsService.SCALES_PORT,
                        String.class
                );
    }

    private int configuredBaudRate() {
        return settingsService == null
                ? baudRate
                : settingInt(settingsService, SettingsService.SCALES_BAUD_RATE);
    }

    private Duration configuredResponseTimeout() {
        return settingsService == null
                ? responseTimeout
                : responseTimeout(settingsService);
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

    private record SerialConfiguration(
            String portName,
            int baudRate,
            int readTimeoutMillis
    ) {
    }
}
