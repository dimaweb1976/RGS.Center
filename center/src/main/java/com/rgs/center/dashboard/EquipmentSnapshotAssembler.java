package com.rgs.center.dashboard;

import com.rgs.center.barcodescanner.BarcodeScannerOperationState;
import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.barcodescanner.BarcodeScannerSnapshot;
import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.dashboard.model.BarcodeEquipmentSnapshot;
import com.rgs.center.dashboard.model.BosonEquipmentSnapshot;
import com.rgs.center.dashboard.model.CameraEquipmentSnapshot;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.GammaProEquipmentSnapshot;
import com.rgs.center.dashboard.model.ManipulatorEquipmentSnapshot;
import com.rgs.center.dashboard.model.NicoleEquipmentSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.ScalesEquipmentSnapshot;
import com.rgs.center.dashboard.model.ShpEquipmentSnapshot;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.gammapro.BosonConnectionStatus;
import com.rgs.center.gammapro.BosonHighVoltageSnapshot;
import com.rgs.center.gammapro.GammaProConnectionStatus;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gammapro.GammaProSnapshot;
import com.rgs.center.gammapro.GammaProStatusView;
import com.rgs.center.manipulator.DobotFeedbackFrame;
import com.rgs.center.manipulator.DobotSafetyState;
import com.rgs.center.manipulator.ManipulatorAnalogInputSnapshot;
import com.rgs.center.manipulator.ManipulatorStatusEvaluator;
import com.rgs.center.manipulator.ManipulatorStatusView;
import com.rgs.center.manipulator.RoboticManipulatorSnapshot;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.scales.ScalesReading;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesSnapshot;
import com.rgs.center.scales.ScalesStability;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.LenovoPcService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.SettingsService;
import com.rgs.center.shp.ShpMeasurements;
import com.rgs.center.shp.ShpService;
import com.rgs.center.shp.ShpSnapshot;
import com.rgs.center.shp.ShpStatusEvaluator;
import com.rgs.center.shp.ShpStatusView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Преобразует snapshots существующих сервисов в единую модель оборудования. */
@Component
public class EquipmentSnapshotAssembler {

    private final CameraService cameraService;
    private final CameraImagingService cameraImagingService;
    private final LenovoPcService lenovoPcService;
    private final NicoleService nicoleService;
    private final ShpService shpService;
    private final ShpStatusEvaluator shpStatusEvaluator;
    private final ScalesService scalesService;
    private final GammaProService gammaProService;
    private final BarcodeScannerService barcodeScannerService;
    private final RoboticManipulatorStreamService manipulatorService;
    private final ManipulatorStatusEvaluator manipulatorStatusEvaluator;
    private final DeviceEnablementService deviceEnablementService;
    private final SettingsService settingsService;
    private final Duration staleAfter;
    private final ZoneId nicoleControllerZone;

    /** Создаёт агрегатор поверх read-only методов существующих сервисов. */
    public EquipmentSnapshotAssembler(
            CameraService cameraService,
            CameraImagingService cameraImagingService,
            LenovoPcService lenovoPcService,
            NicoleService nicoleService,
            ShpService shpService,
            ShpStatusEvaluator shpStatusEvaluator,
            ScalesService scalesService,
            GammaProService gammaProService,
            BarcodeScannerService barcodeScannerService,
            RoboticManipulatorStreamService manipulatorService,
            ManipulatorStatusEvaluator manipulatorStatusEvaluator,
            DeviceEnablementService deviceEnablementService,
            SettingsService settingsService,
            @Value("${rgs.dashboard.stale-after:PT30S}") Duration staleAfter,
            @Value("${rgs.device.nicole.controller-zone:}") String nicoleZone
    ) {
        this.cameraService = cameraService;
        this.cameraImagingService = cameraImagingService;
        this.lenovoPcService = lenovoPcService;
        this.nicoleService = nicoleService;
        this.shpService = shpService;
        this.shpStatusEvaluator = shpStatusEvaluator;
        this.scalesService = scalesService;
        this.gammaProService = gammaProService;
        this.barcodeScannerService = barcodeScannerService;
        this.manipulatorService = manipulatorService;
        this.manipulatorStatusEvaluator = manipulatorStatusEvaluator;
        this.deviceEnablementService = deviceEnablementService;
        this.settingsService = settingsService;
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("staleAfter must be positive");
        }
        this.nicoleControllerZone = nicoleZone == null || nicoleZone.isBlank()
                ? ZoneId.systemDefault()
                : ZoneId.of(nicoleZone);
    }

    /**
     * Собирает модель из памяти; ни один вызванный метод не инициирует I/O.
     *
     * @param collectedAt единый момент сборки
     * @return полный снимок оборудования
     */
    public EquipmentSnapshot assemble(Instant collectedAt) {
        Objects.requireNonNull(collectedAt, "collectedAt");
        GammaProStatusView gammaStatus = gammaProService.statusView();
        return new EquipmentSnapshot(
                collectedAt,
                cameras(collectedAt),
                simpleHealth(
                        DeviceCode.LENOVO_PC,
                        lenovoPcService.getLenovoPc(),
                        collectedAt
                ),
                nicole(collectedAt),
                shp(collectedAt),
                scales(collectedAt),
                gammaPro(gammaStatus, collectedAt),
                boson(gammaStatus, collectedAt),
                barcode(collectedAt),
                manipulator(collectedAt)
        );
    }

    private List<CameraEquipmentSnapshot> cameras(Instant now) {
        List<Device> devices = cameraService.getCameras();
        CameraImagingSnapshot imaging = cameraImagingService.snapshot();
        List<CameraEquipmentSnapshot> result = new ArrayList<>();
        for (int index = 0; index < devices.size(); index++) {
            Device device = devices.get(index);
            String code = index == 0 ? DeviceCode.CAMERA.getCode()
                    : DeviceCode.CAMERA.getCode() + "-" + (index + 1);
            CameraImagingSnapshot current = index == 0 ? imaging : null;
            boolean enabled = device.getStatus() != DeviceStatus.DISABLED;
            DeviceHealth health = health(
                    code,
                    device.getName(),
                    device.getIpAddress(),
                    enabled,
                    connection(device.getStatus()),
                    !enabled ? TechnicalState.DISABLED
                            : device.getStatus() == DeviceStatus.ONLINE
                            ? current != null && current.imageAvailable()
                                    ? TechnicalState.READY
                                    : TechnicalState.NO_DATA
                            : TechnicalState.NO_DATA,
                    !enabled ? OperationalState.DISABLED
                            : current != null && current.recognized()
                            ? OperationalState.COMPLETED
                            : OperationalState.IDLE,
                    deviceInstant(device),
                    List.of(),
                    "",
                    now
            );
            result.add(new CameraEquipmentSnapshot(
                    health,
                    current != null && current.imageAvailable(),
                    current == null ? 0L : current.imageVersion(),
                    current != null && current.recognized(),
                    current == null ? 0 : current.bottles().size(),
                    null,
                    current == null ? "" : current.message()
            ));
        }
        return List.copyOf(result);
    }

    private NicoleEquipmentSnapshot nicole(Instant now) {
        var view = nicoleService.statusView();
        var source = view.snapshot();
        Device nicoleDevice = nicoleService.getNicole();
        boolean enabled = view.connectionStatus()
                != com.rgs.center.nicole.NicoleConnectionStatus.DISABLED;
        ConnectionState connection = switch (view.connectionStatus()) {
            case ONLINE -> ConnectionState.ONLINE;
            case OFFLINE -> ConnectionState.OFFLINE;
            case DISABLED -> ConnectionState.DISABLED;
        };
        TechnicalState technical = switch (view.readiness()) {
            case READY -> TechnicalState.READY;
            case ALERT -> TechnicalState.ALERT;
            case NO_DATA -> TechnicalState.NO_DATA;
            case DISABLED -> TechnicalState.DISABLED;
        };
        List<String> warnings = source == null || view.warningText() == null
                || view.warningText().isBlank() || "-".equals(view.warningText())
                ? List.of() : List.of(view.warningText());
        DeviceHealth health = health(
                DeviceCode.NICOLE.getCode(),
                DeviceCode.NICOLE.getDisplayName(),
                nicoleDevice.getIpAddress(),
                enabled,
                connection,
                technical,
                enabled ? OperationalState.IDLE : OperationalState.DISABLED,
                source == null ? null : source.receivedAt(),
                warnings,
                "",
                now
        );
        return new NicoleEquipmentSnapshot(
                health,
                source == null ? null : source.resetCause(),
                source == null ? null : source.ethernetUp(),
                source == null ? null : source.dhcpLeased(),
                source == null ? null : source.cryoControllerCommunication(),
                source == null ? null : source.cryocoolerStarted(),
                source == null ? null : source.advancedMode(),
                source == null ? null : source.currentNitrogenLevel(),
                source == null ? null : source.currentNitrogenPressure(),
                source == null ? null : source.lowerLevelVoltage(),
                source == null ? null : source.upperLevelVoltage(),
                source == null ? null : source.nitrogenLevelFrequency(),
                source == null || source.controllerTime() == null ? null
                        : source.controllerTime()
                                .atZone(nicoleControllerZone)
                                .toInstant(),
                source == null ? null : source.coolerErrorCode(),
                source == null ? null : source.warningFlags(),
                warnings,
                source == null ? null : source.receivedAt()
        );
    }

    private ShpEquipmentSnapshot shp(Instant now) {
        ShpSnapshot source = shpService.snapshot();
        boolean enabled = deviceEnablementService.isEnabled(DeviceCode.SHP);
        ShpStatusView status = shpStatusEvaluator.evaluate(
                source.connected(), source.measurements()
        );
        ShpMeasurements values = source.measurements();
        Instant observedAt = values == null ? instant(
                source.lastInteractionOutEpochSeconds()
        ) : instant(values.receivedAtEpochSeconds());
        TechnicalState technical = switch (status.readiness()) {
            case READY -> TechnicalState.READY;
            case ALERT -> TechnicalState.ALERT;
            case NO_DATA -> TechnicalState.NO_DATA;
        };
        DeviceHealth health = health(
                source.deviceCode(),
                DeviceCode.SHP.getDisplayName(),
                source.portName(),
                enabled,
                enabled ? source.connected()
                        ? ConnectionState.ONLINE : ConnectionState.OFFLINE
                        : ConnectionState.DISABLED,
                enabled ? technical : TechnicalState.DISABLED,
                enabled ? OperationalState.IDLE : OperationalState.DISABLED,
                observedAt,
                technical == TechnicalState.ALERT
                        ? List.of("SHP thresholds are exceeded") : List.of(),
                source.lastError(),
                now
        );
        return new ShpEquipmentSnapshot(
                health,
                values == null ? null : values.ambientTemperatureCelsius(),
                values == null ? null : values.humidityPercent(),
                values == null ? null : values.atmosphericPressureHpa(),
                values == null ? null : values.detectorCurrent(),
                values == null ? null : values.chargeOutputVoltage(),
                values == null ? null : instant(values.receivedAtEpochSeconds())
        );
    }

    private ScalesEquipmentSnapshot scales(Instant now) {
        ScalesSnapshot source = scalesService.snapshot();
        ScalesReading reading = source.reading();
        boolean enabled = deviceEnablementService.isEnabled(DeviceCode.SCALES);
        TechnicalState technical;
        if (!source.lastError().isBlank()) {
            technical = TechnicalState.ALERT;
        } else if (reading == null) {
            technical = TechnicalState.NO_DATA;
        } else if (reading.stability() == ScalesStability.STABLE) {
            technical = TechnicalState.READY;
        } else if (reading.stability() == ScalesStability.UNSTABLE) {
            technical = TechnicalState.BUSY;
        } else {
            technical = TechnicalState.ALERT;
        }
        DeviceHealth health = health(
                source.deviceCode(),
                DeviceCode.SCALES.getDisplayName(),
                source.portName(),
                enabled,
                enabled ? source.connected()
                        ? ConnectionState.ONLINE : ConnectionState.OFFLINE
                        : ConnectionState.DISABLED,
                enabled ? technical : TechnicalState.DISABLED,
                enabled ? OperationalState.IDLE : OperationalState.DISABLED,
                reading == null ? instant(source.lastResponseEpochSeconds())
                        : instant(reading.receivedAtEpochSeconds()),
                technical == TechnicalState.ALERT && !source.lastError().isBlank()
                        ? List.of(source.lastError()) : List.of(),
                source.lastError(),
                now
        );
        return new ScalesEquipmentSnapshot(
                health,
                reading == null ? null : reading.mass(),
                reading == null ? null : reading.massGrams(),
                reading == null ? "" : reading.unit(),
                reading == null
                        ? ScalesEquipmentSnapshot.MeasurementStability.UNKNOWN
                        : switch (reading.stability()) {
                            case STABLE -> ScalesEquipmentSnapshot
                                    .MeasurementStability.STABLE;
                            case UNSTABLE -> ScalesEquipmentSnapshot
                                    .MeasurementStability.UNSTABLE;
                            case ABOVE_RANGE, BELOW_RANGE -> ScalesEquipmentSnapshot
                                    .MeasurementStability.UNKNOWN;
                        },
                reading == null ? null : instant(reading.receivedAtEpochSeconds())
        );
    }

    private GammaProEquipmentSnapshot gammaPro(
            GammaProStatusView status,
            Instant now
    ) {
        GammaProSnapshot source = status.snapshot();
        boolean enabled = gammaProService.isEnabled();
        ConnectionState connection = enabled
                ? status.connectionStatus() == GammaProConnectionStatus.CONNECTED
                        ? ConnectionState.ONLINE : ConnectionState.OFFLINE
                : ConnectionState.DISABLED;
        TechnicalState technical = source.measurementState()
                == GammaProMeasurementState.FAILED
                ? TechnicalState.ALERT
                : status.lastUpdatedAt() == null
                        ? TechnicalState.NO_DATA : TechnicalState.READY;
        OperationalState operation = switch (source.measurementState()) {
            case IDLE -> OperationalState.IDLE;
            case RUNNING -> OperationalState.RUNNING;
            case COMPLETED -> OperationalState.COMPLETED;
            case FAILED -> OperationalState.FAILED;
        };
        DeviceHealth health = health(
                DeviceCode.GAMMA_PRO.getCode(),
                DeviceCode.GAMMA_PRO.getDisplayName(),
                source.host() + ":" + source.port(),
                enabled,
                connection,
                enabled ? technical : TechnicalState.DISABLED,
                enabled ? operation : OperationalState.DISABLED,
                status.lastUpdatedAt(),
                source.lastError().isBlank()
                        ? List.of() : List.of(source.lastError()),
                source.lastError(),
                now
        );
        return new GammaProEquipmentSnapshot(
                health,
                source.host(),
                source.port(),
                switch (source.measurementState()) {
                    case IDLE -> GammaProEquipmentSnapshot
                            .SpectrumAcquisitionState.IDLE;
                    case RUNNING -> GammaProEquipmentSnapshot
                            .SpectrumAcquisitionState.RUNNING;
                    case COMPLETED -> GammaProEquipmentSnapshot
                            .SpectrumAcquisitionState.COMPLETED;
                    case FAILED -> GammaProEquipmentSnapshot
                            .SpectrumAcquisitionState.FAILED;
                },
                source.mcaName(),
                source.channelName(),
                source.presetSeconds(),
                source.tracts(),
                source.lastCommand(),
                source.lastResponse()
        );
    }

    private BosonEquipmentSnapshot boson(
            GammaProStatusView gammaStatus,
            Instant now
    ) {
        boolean enabled = deviceEnablementService.isEnabled(DeviceCode.BOSON);
        BosonHighVoltageSnapshot source = gammaProService
                .bosonHighVoltageSnapshot();
        boolean connected = gammaStatus.bosonConnectionStatus()
                == BosonConnectionStatus.CONNECTED;
        DeviceHealth health = health(
                DeviceCode.BOSON.getCode(),
                DeviceCode.BOSON.getDisplayName(),
                settingsService.getValue(SettingsService.BOSON_IP, String.class),
                enabled,
                enabled ? connected ? ConnectionState.ONLINE
                        : ConnectionState.OFFLINE : ConnectionState.DISABLED,
                enabled ? connected ? TechnicalState.READY
                        : TechnicalState.NO_DATA : TechnicalState.DISABLED,
                enabled ? OperationalState.IDLE : OperationalState.DISABLED,
                gammaStatus.bosonLastConfirmedAt(),
                source.lastError().isBlank()
                        ? List.of() : List.of(source.lastError()),
                source.lastError(),
                now
        );
        return new BosonEquipmentSnapshot(
                health,
                source.requestedEnabled(),
                source.requestedValue(),
                source.valueType().name(),
                source.commandAccepted(),
                instant(source.commandedAtEpochSeconds()),
                gammaStatus.bosonConnectionStatus().name()
        );
    }

    private BarcodeEquipmentSnapshot barcode(Instant now) {
        BarcodeScannerSnapshot source = barcodeScannerService.snapshot();
        Instant observedAt = latest(
                instant(source.lastHeartbeatEpochSeconds()),
                instant(source.lastScanEpochSeconds())
        );
        TechnicalState technical = !source.lastConnectionError().isBlank()
                || !source.lastScanError().isBlank()
                || source.operationState() == BarcodeScannerOperationState.ERROR
                ? TechnicalState.ALERT
                : source.connected() ? TechnicalState.READY
                        : TechnicalState.NO_DATA;
        List<String> reasons = new ArrayList<>();
        if (!source.lastConnectionError().isBlank()) {
            reasons.add(source.lastConnectionError());
        }
        if (!source.lastScanError().isBlank()) {
            reasons.add(source.lastScanError());
        }
        DeviceHealth health = health(
                source.deviceCode(),
                DeviceCode.BARCODE_SCANNER.getDisplayName(),
                source.portName(),
                source.enabled(),
                source.enabled() ? source.connected()
                        ? ConnectionState.ONLINE : ConnectionState.OFFLINE
                        : ConnectionState.DISABLED,
                source.enabled() ? technical : TechnicalState.DISABLED,
                source.enabled() ? barcodeOperation(source.operationState())
                        : OperationalState.DISABLED,
                observedAt,
                reasons,
                !source.lastConnectionError().isBlank()
                        ? source.lastConnectionError() : source.lastScanError(),
                now
        );
        return new BarcodeEquipmentSnapshot(
                health,
                source.portName(),
                source.baudRate(),
                BarcodeEquipmentSnapshot.BarcodeOperationState.valueOf(
                        source.operationState().name()
                ),
                source.currentAttempt(),
                source.maxAttempts(),
                instant(source.lastHeartbeatEpochSeconds()),
                instant(source.lastScanEpochSeconds()),
                source.lastBarcode(),
                source.lastConnectionError(),
                source.lastScanError()
        );
    }

    private ManipulatorEquipmentSnapshot manipulator(Instant now) {
        boolean enabled = manipulatorService.isEnabled();
        String host = settingsService.getValue(
                SettingsService.MANIPULATOR_HOST, String.class
        );
        int commandPort = settingInt(SettingsService.MANIPULATOR_COMMAND_PORT);
        int feedbackPort = settingInt(SettingsService.MANIPULATOR_FEEDBACK_PORT);
        RoboticManipulatorSnapshot source = manipulatorService.snapshot();
        ManipulatorStatusView status = manipulatorStatusEvaluator.evaluate(
                enabled, source
        );
        DobotFeedbackFrame feedback = source.latestFeedback();
        ManipulatorAnalogInputSnapshot analog = source.hatchAnalogInput();
        DobotSafetyState safety = feedback == null ? null
                : DobotSafetyState.decode(feedback.safetyState());
        int closedBit = settingInt(SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT);
        int openBit = settingInt(SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT);
        Instant receivedAt = instantMillis(
                source.latestFeedbackReceivedAtEpochMillis()
        );
        DeviceHealth health = health(
                DeviceCode.ROBOTIC_MANIPULATOR.getCode(),
                DeviceCode.ROBOTIC_MANIPULATOR.getDisplayName(),
                host + ":" + commandPort,
                enabled,
                switch (status.connectionStatus()) {
                    case DISABLED -> ConnectionState.DISABLED;
                    case CONNECTING -> ConnectionState.CONNECTING;
                    case ONLINE -> ConnectionState.ONLINE;
                    case OFFLINE -> ConnectionState.OFFLINE;
                },
                switch (status.readiness()) {
                    case DISABLED -> TechnicalState.DISABLED;
                    case READY -> TechnicalState.READY;
                    case BUSY -> TechnicalState.BUSY;
                    case ALERT -> TechnicalState.ALERT;
                    case NO_DATA -> TechnicalState.NO_DATA;
                },
                enabled ? feedback != null
                        && (feedback.running() || feedback.queuedCommandRunning())
                        ? OperationalState.RUNNING : OperationalState.IDLE
                        : OperationalState.DISABLED,
                receivedAt,
                status.reasons(),
                source.lastError() == null ? "" : source.lastError(),
                now
        );
        return new ManipulatorEquipmentSnapshot(
                health,
                host,
                commandPort,
                feedbackPort,
                feedback == null || feedback.pose() == null
                        ? new ManipulatorEquipmentSnapshot.Pose(
                                null, null, null, null, null, null
                        )
                        : new ManipulatorEquipmentSnapshot.Pose(
                                feedback.pose().x(), feedback.pose().y(),
                                feedback.pose().z(), feedback.pose().rx(),
                                feedback.pose().ry(), feedback.pose().rz()
                        ),
                feedback == null
                        ? new ManipulatorEquipmentSnapshot.Motion(
                                null, null, null, null, null, null
                        )
                        : new ManipulatorEquipmentSnapshot.Motion(
                                feedback.robotMode(), feedback.enabled(),
                                feedback.running(),
                                feedback.queuedCommandRunning(),
                                feedback.error(), feedback.currentCommandId()
                        ),
                feedback == null
                        ? new ManipulatorEquipmentSnapshot.Io("", "")
                        : new ManipulatorEquipmentSnapshot.Io(
                                hexadecimal(feedback.digitalInputs(), 16),
                                hexadecimal(feedback.digitalOutputs(), 16)
                        ),
                new ManipulatorEquipmentSnapshot.Hatch(
                        ManipulatorEquipmentSnapshot.HatchState.valueOf(
                                status.hatchStatus().name()
                        ),
                        closedBit,
                        feedback == null ? null
                                : bit(feedback.digitalInputs(), closedBit),
                        openBit,
                        feedback == null ? null
                                : bit(feedback.digitalInputs(), openBit),
                        analog != null && analog.enabled(),
                        analog == null ? null : analog.index(),
                        analog == null ? null : analog.value(),
                        analog == null ? null
                                : instantMillis(analog.receivedAtEpochMillis()),
                        analog == null || analog.lastError() == null
                                ? "" : analog.lastError()
                ),
                safety == null
                        ? new ManipulatorEquipmentSnapshot.Safety(
                                "", null, null, null, null, null,
                                null, null, null, List.of()
                        )
                        : new ManipulatorEquipmentSnapshot.Safety(
                                hexadecimal(safety.rawValue(), 2),
                                safety.emergencyStop(), safety.protectiveStop(),
                                safety.reducedMode(), safety.nonStop(),
                                safety.inMotion(), safety.systemEmergencyStop(),
                                safety.userEmergencyStop(),
                                safety.outsideSafetyHome(),
                                safety.blockingFaults()
                        ),
                telemetry(source, feedback, now)
        );
    }

    private ManipulatorEquipmentSnapshot.Telemetry telemetry(
            RoboticManipulatorSnapshot source,
            DobotFeedbackFrame feedback,
            Instant now
    ) {
        PeripheralWorkerSnapshot command = source.commandChannel();
        PeripheralWorkerSnapshot stream = source.feedbackChannel();
        Instant receivedAt = instantMillis(
                source.latestFeedbackReceivedAtEpochMillis()
        );
        return new ManipulatorEquipmentSnapshot.Telemetry(
                workerState(command),
                workerState(stream),
                feedback == null ? null : feedback.controllerTimestampMillis(),
                receivedAt,
                receivedAt == null ? null
                        : Math.max(0L, Duration.between(receivedAt, now).toMillis()),
                stream == null ? 0L : stream.bytesReceived(),
                stream == null ? 0L : stream.framesReceived(),
                stream == null ? 0L : stream.framesDropped()
        );
    }

    private DeviceHealth simpleHealth(
            DeviceCode code,
            Device device,
            Instant now
    ) {
        boolean enabled = device.getStatus() != DeviceStatus.DISABLED;
        return health(
                code.getCode(),
                device.getName(),
                device.getIpAddress(),
                enabled,
                connection(device.getStatus()),
                enabled && device.getStatus() == DeviceStatus.ONLINE
                        ? TechnicalState.READY
                        : enabled ? TechnicalState.NO_DATA
                                : TechnicalState.DISABLED,
                enabled ? OperationalState.IDLE : OperationalState.DISABLED,
                deviceInstant(device),
                List.of(),
                "",
                now
        );
    }

    private DeviceHealth health(
            String code,
            String name,
            String endpoint,
            boolean enabled,
            ConnectionState connection,
            TechnicalState technical,
            OperationalState operation,
            Instant observedAt,
            List<String> reasons,
            String lastError,
            Instant now
    ) {
        return new DeviceHealth(
                code,
                name,
                endpoint,
                enabled,
                connection,
                technical,
                operation,
                observedAt,
                enabled && (observedAt == null
                        || observedAt.plus(staleAfter).isBefore(now)),
                reasons,
                lastError == null ? "" : lastError
        );
    }

    private int settingInt(String code) {
        return Math.toIntExact(settingsService.getValue(code, Long.class));
    }

    private static ConnectionState connection(DeviceStatus status) {
        return switch (status) {
            case DISABLED -> ConnectionState.DISABLED;
            case ONLINE -> ConnectionState.ONLINE;
            case OFFLINE -> ConnectionState.OFFLINE;
        };
    }

    private static OperationalState barcodeOperation(
            BarcodeScannerOperationState state
    ) {
        return switch (state) {
            case IDLE -> OperationalState.IDLE;
            case SCANNING -> OperationalState.RUNNING;
            case SUCCESS -> OperationalState.COMPLETED;
            case TIMEOUT, CANCELLED, ERROR -> OperationalState.FAILED;
        };
    }

    private static Instant deviceInstant(Device device) {
        return device.getLastCheck() == null ? null
                : device.getLastCheck().atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Instant instant(long seconds) {
        return seconds <= 0L ? null : Instant.ofEpochSecond(seconds);
    }

    private static Instant instantMillis(long millis) {
        return millis <= 0L ? null : Instant.ofEpochMilli(millis);
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private static boolean bit(long mask, int index) {
        return index >= 0 && index < Long.SIZE
                && (mask & (1L << index)) != 0L;
    }

    private static String hexadecimal(long value, int digits) {
        return String.format(Locale.ROOT, "0x%0" + digits + "X", value);
    }

    private static String workerState(PeripheralWorkerSnapshot snapshot) {
        return snapshot == null ? "STOPPED" : snapshot.state().name();
    }
}
