package com.rgs.center.gammapro;

import com.rgs.center.connection.TcpDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.service.AbstractQueuedDeviceService;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Самостоятельный TCP-сервис управления программой Gamma-PRO.
 *
 * <p>Соединение запускается общим {@code DeviceConnectionManager}. Сервис
 * периодически выполняет прикладной heartbeat {@code GET_TRACTS_NAME}, а для
 * 7-го этапа автоматического сценария последовательно передаёт тракт, время
 * измерения, запускает набор спектра и ожидает {@code WAIT_MEAS_END=OK}.</p>
 */
@Service
public class GammaProService extends AbstractQueuedDeviceService
        implements GammaProHighVoltageControl {

    private static final Logger log =
            LoggerFactory.getLogger(GammaProService.class);

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;
    private final Charset charset;
    private final ReentrantLock protocolLock = new ReentrantLock();

    private volatile GammaProMeasurementState measurementState =
            GammaProMeasurementState.IDLE;
    private volatile String mcaName = "";
    private volatile String channelName = "";
    private volatile int presetSeconds;
    private volatile List<String> tracts = List.of();
    private volatile String lastResponse = "";
    private volatile String lastError = "";
    private volatile BosonHighVoltageSnapshot bosonHighVoltage =
            BosonHighVoltageSnapshot.initial();

    /**
     * @param settingsService параметры TCP и кодировки
     * @param currentDataService общая витрина состояния устройств
     */
    public GammaProService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        super(
                DeviceCode.GAMMA_PRO,
                createConnection(settingsService),
                new PeripheralWorkerConfiguration(
                        16384,
                        64,
                        128,
                        1024,
                        8192,
                        true
                )
        );
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
        charset = Charset.forName(settingsService.getValue(
                SettingsService.GAMMA_PRO_CHARSET,
                String.class
        ));
    }

    /**
     * Проверяет Gamma-PRO прикладной командой или принимает сообщение о
     * завершении текущего набора спектра.
     */
    @Scheduled(
            fixedDelayString =
                    "${rgs.device.gamma-pro.poll-interval-millis:10000}",
            initialDelayString =
                    "${rgs.device.gamma-pro.poll-initial-delay-millis:0}"
    )
    public void pollGammaPro() {
        if (!isEnabled()) {
            return;
        }
        if (!protocolLock.tryLock()) {
            log.trace("Gamma-PRO poll skipped: another command is active");
            return;
        }
        try {
            if (measurementState == GammaProMeasurementState.RUNNING) {
                pollMeasurementLocked();
            } else {
                heartbeatLocked();
            }
        } catch (RuntimeException exception) {
            if (measurementState == GammaProMeasurementState.RUNNING) {
                measurementState = GammaProMeasurementState.FAILED;
            }
            recordFailure(exception);
        } finally {
            protocolLock.unlock();
        }
    }

    /**
     * Выполняет начало 7-го этапа автоматического сценария.
     *
     * @param request тракт и длительность измерения
     * @return состояние после запуска и передачи WAIT_MEAS_END
     */
    public GammaProSnapshot startSpectrumAcquisition(
            GammaProMeasurementRequest request
    ) {
        protocolLock.lock();
        try {
            if (measurementState == GammaProMeasurementState.RUNNING) {
                throw new GammaProProtocolException(
                        "Gamma-PRO measurement is already running"
                );
            }

            executeCommandLocked(GammaProProtocol.setTract(
                    request.mcaName(),
                    request.channelName()
            ));
            executeCommandLocked(GammaProProtocol.setPreset(
                    request.presetSeconds()
            ));
            executeCommandLocked(GammaProProtocol.START);

            mcaName = request.mcaName();
            channelName = request.channelName();
            presetSeconds = request.presetSeconds();
            measurementState = GammaProMeasurementState.RUNNING;

            submitCommandLocked(GammaProProtocol.WAIT_MEAS_END);
            GammaProProtocol.ResponseLine waitResponse =
                    awaitCommandLineLocked(
                            GammaProProtocol.WAIT_MEAS_END,
                            responseTimeout()
                    );
            applyWaitResponse(waitResponse);
            publishSuccess();
            log.info(
                    "Gamma-PRO acquisition started: mca={}, channel={}, preset={} s",
                    mcaName,
                    channelName,
                    presetSeconds
            );
            return snapshot();
        } catch (RuntimeException exception) {
            measurementState = GammaProMeasurementState.FAILED;
            recordFailure(exception);
            throw exception;
        } finally {
            protocolLock.unlock();
        }
    }

    /**
     * Неблокирующе проверяет поступление {@code WAIT_MEAS_END=OK}.
     *
     * @return актуальный снимок измерения
     */
    public GammaProSnapshot pollSpectrumAcquisition() {
        protocolLock.lock();
        try {
            pollMeasurementLocked();
            return snapshot();
        } catch (RuntimeException exception) {
            recordFailure(exception);
            throw exception;
        } finally {
            protocolLock.unlock();
        }
    }

    /** Останавливает набор спектра командой STOP. */
    public GammaProSnapshot stopSpectrumAcquisition() {
        protocolLock.lock();
        try {
            executeCommandLocked(GammaProProtocol.STOP);
            measurementState = GammaProMeasurementState.COMPLETED;
            publishSuccess();
            return snapshot();
        } catch (RuntimeException exception) {
            measurementState = GammaProMeasurementState.FAILED;
            recordFailure(exception);
            throw exception;
        } finally {
            protocolLock.unlock();
        }
    }

    /**
     * Управляет HV устройства BOSON через выбранный тракт Gamma-PRO.
     * Команды выбора тракта и SET_HV выполняются под общей блокировкой, поэтому
     * не перемешиваются с heartbeat и командами набора спектра.
     */
    @Override
    public BosonHighVoltageSnapshot applyBosonHighVoltage(
            String requestedMcaName,
            String requestedChannelName,
            boolean enabled,
            int value,
            GammaProHighVoltageValueType valueType
    ) {
        protocolLock.lock();
        try {
            if (measurementState == GammaProMeasurementState.RUNNING) {
                throw new GammaProProtocolException(
                        "Cannot change BOSON HV while acquisition is running"
                );
            }
            executeCommandLocked(GammaProProtocol.setTract(
                    requestedMcaName,
                    requestedChannelName
            ));
            currentDataService.setValue(
                    DeviceCode.BOSON,
                    CurrentDataService.INTERACTION_LASTDATE_IN,
                    Instant.now().getEpochSecond()
            );
            executeCommandLocked(GammaProProtocol.setHighVoltage(
                    enabled,
                    value,
                    valueType
            ));

            long confirmedAt = Instant.now().getEpochSecond();
            mcaName = requestedMcaName;
            channelName = requestedChannelName;
            bosonHighVoltage = new BosonHighVoltageSnapshot(
                    requestedMcaName,
                    requestedChannelName,
                    enabled,
                    value,
                    valueType,
                    true,
                    confirmedAt,
                    ""
            );
            currentDataService.recordDataSuccess(
                    DeviceCode.BOSON,
                    bosonHighVoltage
            );
            publishSuccess();
            log.info(
                    "Gamma-PRO accepted BOSON HV command: enabled={}, value={}, type={}, tract={}/{}",
                    enabled,
                    value,
                    valueType,
                    requestedMcaName,
                    requestedChannelName
            );
            return bosonHighVoltage;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            bosonHighVoltage = new BosonHighVoltageSnapshot(
                    requestedMcaName,
                    requestedChannelName,
                    enabled,
                    value,
                    valueType,
                    false,
                    Instant.now().getEpochSecond(),
                    message
            );
            currentDataService.recordDataFailure(DeviceCode.BOSON, message);
            throw exception;
        } finally {
            protocolLock.unlock();
        }
    }

    /** @return последнее состояние команды HV, а не фактическое измерение BOSON */
    public BosonHighVoltageSnapshot bosonHighVoltageSnapshot() {
        return bosonHighVoltage;
    }

    /** @return потокобезопасный снимок сервиса */
    public GammaProSnapshot snapshot() {
        return new GammaProSnapshot(
                currentDataService.getValue(
                        DeviceCode.GAMMA_PRO,
                        CurrentDataService.PINGED,
                        Boolean.class
                ),
                settingsService.getValue(
                        SettingsService.GAMMA_PRO_HOST,
                        String.class
                ),
                settingInt(SettingsService.GAMMA_PRO_PORT),
                measurementState,
                mcaName,
                channelName,
                presetSeconds,
                tracts,
                lastResponse,
                lastError
        );
    }

    /** Выполняет реальный heartbeat и сохраняет список трактов. */
    private void heartbeatLocked() {
        GammaProCommandResult result = executeCommandLocked(
                GammaProProtocol.GET_TRACTS_NAME
        );
        tracts = result.dataLines();
        publishSuccess();
        log.debug("Gamma-PRO heartbeat completed, dataLines={}", tracts.size());
    }

    /** Принимает уже ожидаемый асинхронный результат WAIT_MEAS_END. */
    private void pollMeasurementLocked() {
        if (measurementState != GammaProMeasurementState.RUNNING) {
            return;
        }
        awaitQueuedFrame(Duration.ofMillis(25)).ifPresent(frame -> {
            GammaProProtocol.ResponseLine response = decode(frame);
            if (GammaProProtocol.WAIT_MEAS_END.equals(
                    response.commandName()
            )) {
                applyWaitResponse(response);
                publishSuccess();
            }
        });
    }

    /** Выполняет команду до терминального ответа OK или ER. */
    private GammaProCommandResult executeCommandLocked(String command) {
        submitCommandLocked(command);
        String expectedName = GammaProProtocol.commandName(command);
        List<String> data = new ArrayList<>();
        long deadline = System.nanoTime() + responseTimeout().toNanos();

        while (true) {
            GammaProProtocol.ResponseLine response = awaitCommandLineLocked(
                    expectedName,
                    remaining(deadline, expectedName)
            );
            switch (response.type()) {
                case DATA -> data.add(response.payload());
                case DO -> log.debug(
                        "Gamma-PRO command {} is still running",
                        expectedName
                );
                case OK -> {
                    return new GammaProCommandResult(
                            command,
                            data,
                            response.raw()
                    );
                }
                case ERROR -> throw new GammaProProtocolException(
                        "Gamma-PRO rejected command " + command
                );
                case UNKNOWN -> log.debug(
                        "Ignoring unknown Gamma-PRO response: {}",
                        response.raw()
                );
            }
        }
    }

    /** Ставит одну строку команды в общую TX-очередь. */
    private void submitCommandLocked(String command) {
        currentDataService.setValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );
        submitQueued(GammaProProtocol.encode(command, charset));
        log.debug("Gamma-PRO command queued: {}", command);
    }

    /** Ожидает следующую строку, относящуюся к указанной команде. */
    private GammaProProtocol.ResponseLine awaitCommandLineLocked(
            String expectedCommand,
            Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            byte[] frame = awaitQueuedFrame(
                    remaining(deadline, expectedCommand)
            ).orElseThrow(() -> new GammaProProtocolException(
                    "Gamma-PRO response timed out for " + expectedCommand
            ));
            GammaProProtocol.ResponseLine response = decode(frame);
            if (expectedCommand.equals(response.commandName())) {
                return response;
            }
            log.debug(
                    "Ignoring Gamma-PRO response for another command: {}",
                    response.raw()
            );
        }
    }

    /** Применяет состояние длительной команды ожидания измерения. */
    private void applyWaitResponse(GammaProProtocol.ResponseLine response) {
        switch (response.type()) {
            case DO -> measurementState = GammaProMeasurementState.RUNNING;
            case OK -> {
                measurementState = GammaProMeasurementState.COMPLETED;
                log.info("Gamma-PRO spectrum acquisition completed");
            }
            case ERROR -> {
                measurementState = GammaProMeasurementState.FAILED;
                throw new GammaProProtocolException(
                        "Gamma-PRO failed while waiting for measurement"
                );
            }
            default -> throw new GammaProProtocolException(
                    "Unexpected WAIT_MEAS_END response: " + response.raw()
            );
        }
    }

    private GammaProProtocol.ResponseLine decode(byte[] frame) {
        GammaProProtocol.ResponseLine response =
                GammaProProtocol.parseResponse(new String(frame, charset));
        lastResponse = response.raw();
        currentDataService.setValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Instant.now().getEpochSecond()
        );
        return response;
    }

    private void publishSuccess() {
        boolean wasConnected = currentDataService.getValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.PINGED,
                Boolean.class
        );
        String previousError = lastError;
        lastError = "";
        currentDataService.setValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.PINGED,
                true
        );
        currentDataService.recordDataSuccess(
                DeviceCode.GAMMA_PRO,
                snapshot()
        );
        if (!wasConnected && !previousError.isBlank()) {
            log.info("Gamma-PRO communication restored");
        }
    }

    private void recordFailure(RuntimeException exception) {
        boolean wasConnected = currentDataService.getValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.PINGED,
                Boolean.class
        );
        String previousError = lastError;
        lastError = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        currentDataService.setValue(
                DeviceCode.GAMMA_PRO,
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(
                DeviceCode.GAMMA_PRO,
                lastError
        );
        if (wasConnected || previousError.isBlank()) {
            log.warn("Gamma-PRO communication failed: {}", lastError);
        } else {
            log.debug("Gamma-PRO communication still unavailable: {}", lastError);
        }
    }

    private Duration responseTimeout() {
        return Duration.ofMillis(settingInt(
                SettingsService.GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS
        ));
    }

    private int settingInt(String code) {
        return Math.toIntExact(settingsService.getValue(code, Long.class));
    }

    private static Duration remaining(long deadline, String command) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new GammaProProtocolException(
                    "Gamma-PRO response timed out for " + command
            );
        }
        return Duration.ofNanos(remaining);
    }

    private static TcpDeviceConnection createConnection(
            SettingsService settingsService
    ) {
        return new TcpDeviceConnection(
                "tcp:gamma-pro",
                () -> settingsService.getValue(
                        SettingsService.GAMMA_PRO_HOST,
                        String.class
                ),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.GAMMA_PRO_PORT,
                        Long.class
                )),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.GAMMA_PRO_CONNECT_TIMEOUT_MILLIS,
                        Long.class
                )),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.GAMMA_PRO_READ_TIMEOUT_MILLIS,
                        Long.class
                ))
        );
    }
}
