package com.rgs.center.barcodescanner;

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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** Managed RS-232 connection and scan state machine for Honeywell Vuquest. */
@Component
public class JSerialCommBarcodeScannerPort
        extends AbstractQueuedDeviceService
        implements BarcodeScannerPort {

    private static final Logger log =
            LoggerFactory.getLogger(JSerialCommBarcodeScannerPort.class);

    private final CurrentDataService currentDataService;
    private final SettingsService settingsService;
    private final BarcodeScannerFrameDecoder frameDecoder;
    private final String portName;
    private final int baudRate;
    private final Duration responseTimeout;
    private final int maxScanAttempts;
    private final Duration retryDelay;
    private final ReentrantLock operationLock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean responsive = new AtomicBoolean();
    private final AtomicReference<BarcodeScannerOperationState> operationState =
            new AtomicReference<>(BarcodeScannerOperationState.IDLE);
    private final AtomicInteger currentAttempt = new AtomicInteger();
    private final AtomicLong lastHeartbeatEpochSeconds = new AtomicLong();
    private final AtomicLong lastScanEpochSeconds = new AtomicLong();
    private final AtomicReference<String> lastBarcode =
            new AtomicReference<>("");
    private final AtomicReference<String> lastConnectionError =
            new AtomicReference<>("");
    private final AtomicReference<String> lastScanError =
            new AtomicReference<>("");

    @Autowired
    public JSerialCommBarcodeScannerPort(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this(
                settingsService,
                currentDataService,
                productionConfiguration(settingsService)
        );
    }

    private JSerialCommBarcodeScannerPort(
            CurrentDataService currentDataService,
            ScannerConfiguration configuration
    ) {
        this(null, currentDataService, configuration);
    }

    private JSerialCommBarcodeScannerPort(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            ScannerConfiguration configuration
    ) {
        this(
                settingsService,
                currentDataService,
                configuration,
                new BarcodeScannerFrameDecoder(
                        configuration.maxCodeLength(),
                        configuration.dataSuffix()
                )
        );
    }

    private JSerialCommBarcodeScannerPort(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            ScannerConfiguration configuration,
            BarcodeScannerFrameDecoder frameDecoder
    ) {
        super(
                DeviceCode.BARCODE_SCANNER,
                configuration.transport(),
                PeripheralWorkerConfiguration.lineDevice(
                        configuration.maxCodeLength()
                ),
                frameDecoder
        );
        this.currentDataService = currentDataService;
        this.settingsService = settingsService;
        this.frameDecoder = frameDecoder;
        portName = configuration.portName();
        baudRate = configuration.baudRate();
        responseTimeout = configuration.responseTimeout();
        maxScanAttempts = configuration.maxScanAttempts();
        retryDelay = configuration.retryDelay();
    }

    /** Package-visible constructor for protocol integration tests. */
    JSerialCommBarcodeScannerPort(
            CurrentDataService currentDataService,
            PeripheralTransport transport,
            String portName,
            int baudRate,
            int maxCodeLength,
            Duration responseTimeout,
            int maxScanAttempts,
            Duration retryDelay
    ) {
        this(
                currentDataService,
                transport,
                portName,
                baudRate,
                maxCodeLength,
                responseTimeout,
                maxScanAttempts,
                retryDelay,
                BarcodeScannerDataSuffix.AUTO
        );
    }

    /** Package-visible constructor with explicit barcode framing for tests. */
    JSerialCommBarcodeScannerPort(
            CurrentDataService currentDataService,
            PeripheralTransport transport,
            String portName,
            int baudRate,
            int maxCodeLength,
            Duration responseTimeout,
            int maxScanAttempts,
            Duration retryDelay,
            BarcodeScannerDataSuffix dataSuffix
    ) {
        this(
                currentDataService,
                new ScannerConfiguration(
                        transport,
                        portName,
                        baudRate,
                        maxCodeLength,
                        responseTimeout,
                        maxScanAttempts,
                        retryDelay,
                        dataSuffix
                )
        );
    }

    /** Queries the device itself; an open COM handle alone is not ONLINE. */
    @Scheduled(
            fixedDelayString =
                    "${rgs.device.barcode-scanner.status-interval-millis:10000}",
            initialDelayString =
                    "${rgs.device.barcode-scanner.status-initial-delay-millis:0}"
    )
    public void refreshConnectionStatus() {
        if (!isEnabled()) {
            responsive.set(false);
            initialized.set(false);
            return;
        }
        if (!operationLock.tryLock()) {
            log.debug("Scanner heartbeat skipped during an active operation");
            return;
        }
        try {
            boolean wasInitialized = initialized.get();
            ensureInitializedLocked();
            if (wasInitialized) {
                probeLocked();
            }
        } catch (RuntimeException exception) {
            registerConnectionFailure(exception);
        } finally {
            operationLock.unlock();
        }
    }

    /** Executes a complete illuminate-trigger-read-cleanup cycle with retries. */
    @Override
    public String readCode(
            String configuredPortName,
            int configuredBaudRate,
            int timeoutMillis,
            int maxCodeLength
    ) {
        try {
            operationLock.lockInterruptibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BarcodeScannerCancelledException(
                    "Barcode scan was cancelled before it started",
                    exception
            );
        }
        try {
            validateReadArguments(
                    configuredPortName,
                    configuredBaudRate,
                    timeoutMillis,
                    maxCodeLength
            );
            ensureInitializedLocked();
            operationState.set(BarcodeScannerOperationState.SCANNING);
            currentAttempt.set(0);
            lastScanError.set("");

            BarcodeScannerNoReadException noRead = null;
            for (int attempt = 1; attempt <= maxScanAttempts; attempt++) {
                currentAttempt.set(attempt);
                try {
                    String barcode = performAttemptLocked(
                            Duration.ofMillis(timeoutMillis),
                            maxCodeLength
                    );
                    lastBarcode.set(barcode);
                    lastScanEpochSeconds.set(Instant.now().getEpochSecond());
                    lastScanError.set("");
                    operationState.set(BarcodeScannerOperationState.SUCCESS);
                    responsive.set(true);
                    return barcode;
                } catch (BarcodeScannerNoReadException exception) {
                    noRead = exception;
                    if (attempt < maxScanAttempts) {
                        pauseBeforeRetry();
                    }
                }
            }

            String message = noRead == null
                    ? "Barcode was not read"
                    : noRead.getMessage();
            lastScanEpochSeconds.set(Instant.now().getEpochSecond());
            lastScanError.set(message);
            operationState.set(BarcodeScannerOperationState.TIMEOUT);
            throw new BarcodeScannerNoReadException(message);
        } catch (BarcodeScannerCancelledException exception) {
            lastScanEpochSeconds.set(Instant.now().getEpochSecond());
            lastScanError.set(exception.getMessage());
            operationState.set(BarcodeScannerOperationState.CANCELLED);
            throw exception;
        } catch (BarcodeScannerException exception) {
            lastScanEpochSeconds.set(Instant.now().getEpochSecond());
            lastScanError.set(exception.getMessage());
            if (!(exception instanceof BarcodeScannerNoReadException)) {
                operationState.set(BarcodeScannerOperationState.ERROR);
            }
            if (exception.isConnectionFailure()) {
                registerConnectionFailure(exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            BarcodeScannerException wrapped = new BarcodeScannerException(
                    "Cannot communicate with barcode scanner on "
                            + configuredPortName(),
                    exception
            );
            lastScanEpochSeconds.set(Instant.now().getEpochSecond());
            lastScanError.set(wrapped.getMessage());
            operationState.set(BarcodeScannerOperationState.ERROR);
            registerConnectionFailure(wrapped);
            throw wrapped;
        } finally {
            operationLock.unlock();
        }
    }

    /** Returns a non-blocking diagnostic snapshot for the web page. */
    public BarcodeScannerSnapshot snapshot() {
        var communication = communicationSnapshot();
        boolean enabled = isEnabled();
        return new BarcodeScannerSnapshot(
                DeviceCode.BARCODE_SCANNER.getCode(),
                configuredPortName(),
                enabled,
                enabled
                        && responsive.get()
                        && communication.state() == PeripheralWorkerState.RUNNING,
                initialized.get(),
                configuredBaudRate(),
                operationState.get(),
                currentAttempt.get(),
                maxScanAttempts,
                lastHeartbeatEpochSeconds.get(),
                lastScanEpochSeconds.get(),
                lastBarcode.get(),
                lastConnectionError.get(),
                lastScanError.get(),
                communication
        );
    }

    private void ensureInitializedLocked() {
        if (communicationSnapshot().state() != PeripheralWorkerState.RUNNING) {
            initialized.set(false);
            responsive.set(false);
            startCommunication();
        }
        if (initialized.get()) {
            return;
        }

        clearQueuedFrames();
        frameDecoder.expectCommandResponse();
        submitQueued(BarcodeScannerProtocol.deactivateTrigger());
        requireAcknowledgement(BarcodeScannerProtocol.manualTriggerNormal());
        requireAcknowledgement(BarcodeScannerProtocol.manualIlluminationHigh());
        requireAcknowledgement(BarcodeScannerProtocol.aimerOff());
        requireAcknowledgement(BarcodeScannerProtocol.illuminationOff());
        probeLocked();
        initialized.set(true);
        log.info(
                "Barcode scanner initialized on {} at {} baud",
                configuredPortName(),
                configuredBaudRate()
        );
    }

    private void probeLocked() {
        long now = Instant.now().getEpochSecond();
        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                now
        );
        requireAcknowledgement(BarcodeScannerProtocol.aimerQuery());
        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                now
        );
        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.PINGED,
                true
        );
        lastHeartbeatEpochSeconds.set(now);
        lastConnectionError.set("");
        responsive.set(true);
    }

    private String performAttemptLocked(
            Duration timeout,
            int maxCodeLength
    ) {
        clearQueuedFrames();
        try {
            requireAcknowledgement(BarcodeScannerProtocol.illuminationOn());
            frameDecoder.expectBarcode();
            submitQueued(BarcodeScannerProtocol.activateTrigger());
            return awaitValidBarcode(timeout, maxCodeLength);
        } finally {
            cleanupAfterAttemptLocked();
        }
    }

    private String awaitValidBarcode(Duration timeout, int maxCodeLength) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new BarcodeScannerCancelledException(
                        "Barcode scan was cancelled",
                        null
                );
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw noRead(timeout);
            }
            byte[] frame;
            try {
                frame = awaitQueuedFrame(Duration.ofNanos(remaining))
                        .orElseThrow(() -> noRead(timeout));
            } catch (RuntimeException exception) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new BarcodeScannerCancelledException(
                            "Barcode scan was cancelled",
                            exception
                    );
                }
                throw exception;
            }
            String barcode = decodeBarcode(frame, maxCodeLength);
            if (barcode != null) {
                return barcode;
            }
            log.debug("Ignoring invalid barcode frame, bytes={}", frame.length);
        }
    }

    private BarcodeScannerNoReadException noRead(Duration timeout) {
        return new BarcodeScannerNoReadException(
                "Barcode was not read within " + timeout.toMillis() + " ms"
        );
    }

    private String decodeBarcode(byte[] frame, int maxCodeLength) {
        if (frame.length == 0 || frame.length > maxCodeLength) {
            return null;
        }
        for (byte value : frame) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned < 0x20 || unsigned > 0x7E) {
                return null;
            }
        }
        String value = new String(frame, StandardCharsets.US_ASCII).trim();
        return value.isEmpty() ? null : value;
    }

    private void cleanupAfterAttemptLocked() {
        boolean interrupted = Thread.interrupted();
        RuntimeException failure = null;
        try {
            frameDecoder.expectCommandResponse();
            submitQueued(BarcodeScannerProtocol.deactivateTrigger());
            requireAcknowledgement(BarcodeScannerProtocol.illuminationOff());
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void requireAcknowledgement(byte[] command) {
        frameDecoder.expectCommandResponse();
        submitQueued(command);
        long deadline = System.nanoTime()
                + configuredResponseTimeout().toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new BarcodeScannerException(
                        "Scanner menu response timed out"
                );
            }
            byte[] frame = awaitQueuedFrame(Duration.ofNanos(remaining))
                    .orElseThrow(() -> new BarcodeScannerException(
                            "Scanner menu response timed out"
                    ));
            BarcodeScannerProtocol.MenuResponse response;
            try {
                response = BarcodeScannerProtocol.parseMenuResponse(frame);
            } catch (BarcodeScannerException unrelatedFrame) {
                log.debug(
                        "Ignoring non-menu frame while waiting for scanner ACK, bytes={}",
                        frame.length
                );
                continue;
            }
            if (!response.acknowledged()) {
                throw new BarcodeScannerException(
                        "Scanner rejected menu command "
                                + response.echoedCommand()
                                + ": " + response.status()
                );
            }
            responsive.set(true);
            return;
        }
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BarcodeScannerCancelledException(
                    "Barcode scan was cancelled before retry",
                    exception
            );
        }
    }

    private void registerConnectionFailure(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        responsive.set(false);
        initialized.set(false);
        lastConnectionError.set(message);
        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(
                DeviceCode.BARCODE_SCANNER,
                message
        );
        try {
            stopCommunication();
        } catch (RuntimeException stopFailure) {
            exception.addSuppressed(stopFailure);
        }
        log.warn("Barcode scanner communication failed: {}", message);
    }

    private void validateReadArguments(
            String configuredPortName,
            int configuredBaudRate,
            int timeoutMillis,
            int maxCodeLength
    ) {
        if (!configuredPortName().equals(configuredPortName)
                || configuredBaudRate() != configuredBaudRate) {
            throw new BarcodeScannerException(
                    "Barcode scanner settings changed; restart is required",
                    null,
                    false
            );
        }
        if (timeoutMillis <= 0) {
            throw new BarcodeScannerException(
                    "Barcode scan timeout must be positive",
                    null,
                    false
            );
        }
        if (maxCodeLength <= 0) {
            throw new BarcodeScannerException(
                    "Maximum barcode length must be positive",
                    null,
                    false
            );
        }
    }

    private static ScannerConfiguration productionConfiguration(
            SettingsService settingsService
    ) {
        String portName = settingsService.getValue(
                SettingsService.BARCODE_SCANNER_PORT,
                String.class
        );
        int baudRate = settingInt(
                settingsService,
                SettingsService.BARCODE_SCANNER_BAUD_RATE
        );
        int readTimeout = settingInt(
                settingsService,
                SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS
        );
        int maxCodeLength = settingInt(
                settingsService,
                SettingsService.BARCODE_SCANNER_MAX_CODE_LENGTH
        );
        return new ScannerConfiguration(
                new SerialDeviceConnection(
                        "serial:barcode-scanner",
                        () -> settingsService.getValue(
                                SettingsService.BARCODE_SCANNER_PORT,
                                String.class
                        ),
                        () -> settingInt(
                                settingsService,
                                SettingsService.BARCODE_SCANNER_BAUD_RATE
                        ),
                        () -> settingInt(
                                settingsService,
                                SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS
                        )
                ),
                portName,
                baudRate,
                maxCodeLength,
                Duration.ofMillis(settingInt(
                        settingsService,
                        SettingsService.BARCODE_SCANNER_RESPONSE_TIMEOUT_MILLIS
                )),
                settingInt(
                        settingsService,
                        SettingsService.BARCODE_SCANNER_MAX_SCAN_ATTEMPTS
                ),
                Duration.ofMillis(settingInt(
                        settingsService,
                        SettingsService.BARCODE_SCANNER_RETRY_DELAY_MILLIS
                )),
                BarcodeScannerDataSuffix.fromConfiguration(
                        settingsService.getValue(
                                SettingsService.BARCODE_SCANNER_DATA_SUFFIX,
                                String.class
                        )
                )
        );
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

    private String configuredPortName() {
        return settingsService == null
                ? portName
                : settingsService.getValue(
                        SettingsService.BARCODE_SCANNER_PORT,
                        String.class
                );
    }

    private int configuredBaudRate() {
        return settingsService == null
                ? baudRate
                : settingInt(
                        settingsService,
                        SettingsService.BARCODE_SCANNER_BAUD_RATE
                );
    }

    private Duration configuredResponseTimeout() {
        return settingsService == null
                ? responseTimeout
                : Duration.ofMillis(settingInt(
                        settingsService,
                        SettingsService.BARCODE_SCANNER_RESPONSE_TIMEOUT_MILLIS
                ));
    }

    private record ScannerConfiguration(
            PeripheralTransport transport,
            String portName,
            int baudRate,
            int maxCodeLength,
            Duration responseTimeout,
            int maxScanAttempts,
            Duration retryDelay,
            BarcodeScannerDataSuffix dataSuffix
    ) {
        private ScannerConfiguration {
            if (baudRate <= 0 || maxCodeLength <= 0 || maxScanAttempts <= 0) {
                throw new IllegalArgumentException(
                        "Barcode scanner numeric settings must be positive"
                );
            }
            if (responseTimeout == null
                    || responseTimeout.isZero()
                    || responseTimeout.isNegative()) {
                throw new IllegalArgumentException(
                        "Barcode scanner response timeout must be positive"
                );
            }
            if (retryDelay == null || retryDelay.isNegative()) {
                throw new IllegalArgumentException(
                        "Barcode scanner retry delay must not be negative"
                );
            }
            if (dataSuffix == null) {
                throw new IllegalArgumentException(
                        "Barcode scanner data suffix must not be null"
                );
            }
        }
    }
}
