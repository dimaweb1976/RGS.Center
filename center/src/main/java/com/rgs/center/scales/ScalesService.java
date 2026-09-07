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
                currentDataService,
                serialConfiguration(settingsService),
                responseTimeout(settingsService)
        );
    }

    private ScalesService(
            CurrentDataService currentDataService,
            SerialConfiguration serial,
            Duration responseTimeout
    ) {
        this(
                currentDataService,
                new SerialDeviceConnection(
                        serial.portName(),
                        serial.baudRate(),
                        serial.readTimeoutMillis()
                ),
                serial.portName(),
                serial.baudRate(),
                responseTimeout
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
        super(
                DeviceCode.SCALES,
                transport,
                PeripheralWorkerConfiguration.lineDevice(MAX_FRAME_LENGTH)
        );
        this.currentDataService = currentDataService;
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
                    responseTimeout
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
                portName,
                responsive.get()
                        && communication.state() == PeripheralWorkerState.RUNNING,
                baudRate,
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

    private record SerialConfiguration(
            String portName,
            int baudRate,
            int readTimeoutMillis
    ) {
    }
}
