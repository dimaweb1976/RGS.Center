package com.rgs.center.web.wizard;

import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.barcodescanner.BarcodeScannerSnapshot;
import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.dashboard.model.CameraEquipmentSnapshot;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.ManipulatorEquipmentSnapshot;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gdrm.GdrmService;
import com.rgs.center.gdrm.GdrmSnapshot;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesSnapshot;
import com.rgs.center.service.NicoleService;
import com.rgs.center.shp.ShpService;
import com.rgs.center.shp.ShpSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.rgs.center.web.wizard.WizardServiceDeviceView.Action;
import static com.rgs.center.web.wizard.WizardServiceDeviceView.Communication;
import static com.rgs.center.web.wizard.WizardServiceDeviceView.DeviceCard;
import static com.rgs.center.web.wizard.WizardServiceDeviceView.Metric;
import static com.rgs.center.web.wizard.WizardServiceDeviceView.Section;

/** Converts in-memory device snapshots into the unified service UI. */
@Component
public class WizardServiceDeviceViewFactory {

    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final NicoleService nicole;
    private final ShpService shp;
    private final ScalesService scales;
    private final BarcodeScannerService barcode;
    private final GammaProService gammaPro;
    private final RoboticManipulatorStreamService manipulator;
    private final GdrmService gdrm;
    private final DeviceEnablementService enablement;

    public WizardServiceDeviceViewFactory(
            NicoleService nicole,
            ShpService shp,
            ScalesService scales,
            BarcodeScannerService barcode,
            GammaProService gammaPro,
            RoboticManipulatorStreamService manipulator,
            GdrmService gdrm,
            DeviceEnablementService enablement
    ) {
        this.nicole = nicole;
        this.shp = shp;
        this.scales = scales;
        this.barcode = barcode;
        this.gammaPro = gammaPro;
        this.manipulator = manipulator;
        this.gdrm = gdrm;
        this.enablement = enablement;
    }

    public WizardServiceDeviceView.Overview overview(DashboardSnapshot snapshot) {
        List<DeviceCard> devices = new ArrayList<>();
        for (DeviceHealth health : snapshot.equipment().healthSummary()) {
            devices.add(card(health, protocol(health.code())));
        }
        devices.add(gdrmCard());
        return new WizardServiceDeviceView.Overview(
                snapshot.equipment().collectedAt(), devices
        );
    }

    public WizardServiceDeviceView detail(
            String requestedCode,
            DashboardSnapshot snapshot
    ) {
        String code = normalize(requestedCode);
        EquipmentSnapshot equipment = snapshot.equipment();
        if (code.startsWith("camera")) {
            CameraEquipmentSnapshot camera = equipment.cameras().stream()
                    .filter(value -> normalize(value.health().code()).equals(code))
                    .findFirst()
                    .orElseThrow(() -> notFound(requestedCode));
            return camera(camera);
        }
        return switch (code) {
            case "lenovo-pc" -> simple(
                    card(equipment.lenovoPc(), "LAN reachability"),
                    "LenovoPC is monitored without a command queue."
            );
            case "nicole" -> nicole(equipment);
            case "boson" -> boson(equipment);
            case "roboticmanipulator", "manipulator" -> manipulator(equipment);
            case "barcode-scanner" -> barcode(equipment);
            case "scales" -> scales(equipment);
            case "shp" -> shp(equipment);
            case "gamma-pro" -> gammaPro(equipment);
            case "gdrm" -> gdrm();
            default -> throw notFound(requestedCode);
        };
    }

    private WizardServiceDeviceView camera(CameraEquipmentSnapshot camera) {
        return view(
                card(camera.health(), "HTTP camera snapshot"),
                List.of(section("Image",
                        metric("Available", yesNo(camera.imageAvailable())),
                        metric("Version", camera.imageVersion()),
                        metric("Recognized", yesNo(camera.recognized())),
                        metric("Detected bottles", camera.detectedBottleCount()),
                        metric("Captured", time(camera.capturedAt())),
                        metric("Message", text(camera.message())))),
                List.of(),
                camera.health().code().equals("camera")
                        ? List.of(action("capture", "Capture image",
                        "Request and save a new frame from the configured source.",
                        camera.health().enabled(), false))
                        : List.of()
        );
    }

    private WizardServiceDeviceView nicole(EquipmentSnapshot equipment) {
        var value = equipment.nicole();
        return view(
                card(value.health(), "NICOLE PING/PONG 0x00/0x10"),
                List.of(
                        section("Controller",
                                metric("Reset cause", value.resetCause()),
                                metric("Ethernet", upDown(value.ethernetUp())),
                                metric("DHCP lease", yesNo(value.dhcpLeased())),
                                metric("Cryo controller link", upDown(value.cryoControllerCommunication())),
                                metric("Controller time", time(value.controllerTime())),
                                metric("PONG received", time(value.pongReceivedAt()))),
                        section("Cryocooler and nitrogen",
                                metric("Cryocooler", startedStopped(value.cryocoolerStarted())),
                                metric("Advanced mode", yesNo(value.advancedMode())),
                                metric("Nitrogen level", value.currentNitrogenLevel()),
                                metric("Nitrogen pressure", value.currentNitrogenPressure()),
                                metric("Lower level voltage", value.lowerLevelVoltage()),
                                metric("Upper level voltage", value.upperLevelVoltage()),
                                metric("Level frequency", value.nitrogenLevelFrequency()),
                                metric("Cooler error", value.coolerErrorCode()),
                                metric("Warning flags", value.warningFlags()),
                                metric("Warnings", value.warnings().isEmpty()
                                        ? "None" : String.join(", ", value.warnings())))
                ),
                List.of(communication("TCP protocol worker", nicole.communicationSnapshot())),
                List.of(action("ping", "Request PING/PONG",
                        "Run one whitelisted NICOLE status exchange.",
                        value.health().enabled(), false))
        );
    }

    private WizardServiceDeviceView boson(EquipmentSnapshot equipment) {
        var value = equipment.boson();
        boolean controls = value.health().enabled()
                && equipment.gammaPro().health().connection().name().equals("ONLINE")
                && equipment.gammaPro().acquisitionState().name().equals("IDLE");
        return view(
                card(value.health(), "Gamma-PRO commands to BOSON tract"),
                List.of(section("High voltage request",
                        metric("Requested", yesNo(value.highVoltageRequested())),
                        metric("Requested value", value.requestedHighVoltage() == null
                                ? "No data" : value.requestedHighVoltage() + " V"),
                        metric("Value type", value.highVoltageValueType()),
                        metric("Command accepted", yesNo(value.commandAccepted())),
                        metric("Accepted at", time(value.commandAcceptedAt())),
                        metric("Source status", value.sourceStatus()))),
                List.of(communication("Gamma-PRO TCP worker", gammaPro.communicationSnapshot())),
                List.of(
                        action("select-tract", "Select BOSON tract",
                                "Select the configured MCA/channel without changing HV.", controls, false),
                        action("hv-on", "Turn on HV",
                                "Send the configured standard HV request through Gamma-PRO.", controls, true),
                        action("hv-off", "Turn off HV",
                                "Request safe HV shutdown through Gamma-PRO.", controls, true)
                )
        );
    }

    private WizardServiceDeviceView barcode(EquipmentSnapshot equipment) {
        var value = equipment.barcodeScanner();
        BarcodeScannerSnapshot source = barcode.snapshot();
        boolean scanning = value.scanState().name().equals("SCANNING");
        return view(
                card(value.health(), "RS-232 serial trigger protocol"),
                List.of(section("Scanner",
                        metric("COM port", value.portName()),
                        metric("Baud rate", value.baudRate()),
                        metric("Initialized", yesNo(source.initialized())),
                        metric("Scan state", value.scanState()),
                        metric("Attempt", value.currentAttempt() + " / " + value.maxAttempts()),
                        metric("Last heartbeat", time(value.lastHeartbeatAt())),
                        metric("Last successful scan", time(value.lastSuccessfulScanAt())),
                        metric("Barcode", value.lastBarcode()),
                        metric("Connection error", value.lastConnectionError()),
                        metric("Scan error", value.lastScanError()))),
                List.of(communication("Serial scanner worker", source.communication())),
                List.of(
                        action("scan", "Start scan",
                                "Turn on illumination and serial trigger using the approved sequence.",
                                value.health().enabled() && !scanning, false),
                        action("cancel", "Cancel scan",
                                "Send trigger-off and illumination-off.", scanning, true)
                )
        );
    }

    private WizardServiceDeviceView scales(EquipmentSnapshot equipment) {
        var value = equipment.scales();
        ScalesSnapshot source = scales.snapshot();
        return view(
                card(value.health(), "RADWAG SI immediate-mass command"),
                List.of(section("Measurement",
                        metric("Mass", value.mass()),
                        metric("Mass in grams", value.massGrams()),
                        metric("Unit", value.unit()),
                        metric("Stability", value.stability()),
                        metric("Reading received", time(value.readingReceivedAt())),
                        metric("Baud rate", source.baudRate()))),
                List.of(communication("Serial scales worker", source.communication())),
                List.of(action("read", "Read current weight",
                        "Send one SI command; unstable values are shown but not accepted by measurements.",
                        value.health().enabled(), false))
        );
    }

    private WizardServiceDeviceView shp(EquipmentSnapshot equipment) {
        var value = equipment.shp();
        ShpSnapshot source = shp.snapshot();
        return view(
                card(value.health(), "SHP binary C0/0C protocol"),
                List.of(section("Detector and environment",
                        metric("Ambient temperature", decimal(value.ambientTemperatureCelsius(), " °C")),
                        metric("Humidity", decimal(value.humidityPercent(), " %")),
                        metric("Pressure", decimal(value.atmosphericPressureHpa(), " hPa")),
                        metric("Leakage current", decimal(value.leakageCurrent(), "")),
                        metric("Charge output voltage", decimal(value.chargeOutputVoltage(), " V")),
                        metric("Measurements received", time(value.measurementsReceivedAt())),
                        metric("Baud rate", source.baudRate()))),
                List.of(communication("Serial SHP worker", source.communication())),
                List.of(action("poll", "Read all SHP values",
                        "Run one complete whitelisted SHP polling cycle.",
                        value.health().enabled(), false))
        );
    }

    private WizardServiceDeviceView gammaPro(EquipmentSnapshot equipment) {
        var value = equipment.gammaPro();
        return view(
                card(value.health(), "Gamma-PRO TCP text protocol"),
                List.of(section("Protocol state",
                        metric("Server", value.host() + ":" + value.port()),
                        metric("Acquisition", value.acquisitionState()),
                        metric("MCA", value.mcaName()),
                        metric("Channel", value.channelName()),
                        metric("Preset", value.presetSeconds() == null
                                ? "No data" : value.presetSeconds() + " s"),
                        metric("Available tracts", value.tracts().isEmpty()
                                ? "None" : String.join(", ", value.tracts())),
                        metric("Last command", value.lastCommand()),
                        metric("Last response", value.lastResponse()))),
                List.of(communication("Gamma-PRO TCP worker", gammaPro.communicationSnapshot())),
                List.of(action("heartbeat", "Request tract list",
                        "Run one GET_TRACTS_NAME application heartbeat.",
                        value.health().enabled()
                                && !value.acquisitionState().name().equals("RUNNING"), false))
        );
    }

    private WizardServiceDeviceView manipulator(EquipmentSnapshot equipment) {
        ManipulatorEquipmentSnapshot value = equipment.manipulator();
        var source = manipulator.snapshot();
        return view(
                card(value.health(), "Dobot command 29999 / feedback 30006"),
                List.of(
                        section("Pose",
                                metric("X", value.pose().x()), metric("Y", value.pose().y()),
                                metric("Z", value.pose().z()), metric("Rx", value.pose().rx()),
                                metric("Ry", value.pose().ry()), metric("Rz", value.pose().rz())),
                        section("Motion and I/O",
                                metric("Robot mode", value.motion().robotMode()),
                                metric("Enabled", yesNo(value.motion().enabled())),
                                metric("Running", yesNo(value.motion().running())),
                                metric("Queued command", yesNo(value.motion().queuedCommandRunning())),
                                metric("Current command ID", value.motion().currentCommandId()),
                                metric("Digital inputs", value.io().digitalInputsHex()),
                                metric("Digital outputs", value.io().digitalOutputsHex())),
                        section("Hatch and safety",
                                metric("Hatch", value.hatch().state()),
                                metric("Closed limit", yesNo(value.hatch().closedLimit())),
                                metric("Open limit", yesNo(value.hatch().openLimit())),
                                metric("Emergency stop", yesNo(value.safety().emergencyStop())),
                                metric("Protective stop", yesNo(value.safety().protectiveStop())),
                                metric("Blocking faults", value.safety().blockingFaults().isEmpty()
                                        ? "None" : String.join(", ", value.safety().blockingFaults()))),
                        section("Feedback",
                                metric("Feedback received", time(value.telemetry().feedbackReceivedAt())),
                                metric("Feedback age", value.telemetry().feedbackAgeMillis() == null
                                        ? "No data" : value.telemetry().feedbackAgeMillis() + " ms"),
                                metric("Valid frames", value.telemetry().validFrames()),
                                metric("Dropped frames", value.telemetry().droppedFrames()),
                                metric("Router routes", source.router().queueDepths().size()))
                ),
                List.of(
                        communication("Dobot command channel", source.commandChannel()),
                        communication("Dobot feedback channel", source.feedbackChannel())
                ),
                List.of()
        );
    }

    private WizardServiceDeviceView gdrm() {
        GdrmSnapshot source = gdrm.snapshot();
        List<Section> sections = new ArrayList<>();
        sections.add(section("Identification",
                metric("Address", source.address()),
                metric("Serial number", source.serialNumber()),
                metric("Firmware", source.firmwareVersion()),
                metric("Baud rate", source.baudRate())));
        if (source.measurement() != null) {
            sections.add(section("Dose measurement",
                    metric("Weighted dose rate", decimal(source.measurement().weightedDoseRateSvPerHour(), " Sv/h")),
                    metric("Relative statistical error", source.measurement().weightedRelativeStatisticalError()),
                    metric("Accumulated dose", decimal(source.measurement().accumulatedDoseSv(), " Sv")),
                    metric("Accumulation time", decimal(source.measurement().accumulationTimeSeconds(), " s")),
                    metric("Device status", source.measurement().deviceStatus()),
                    metric("Received", epochSeconds(source.measurement().receivedAtEpochSeconds()))));
        }
        if (source.operatingData() != null) {
            sections.add(section("Operating data",
                    metric("Operating voltage", decimal(source.operatingData().operatingVoltageVolts(), " V")),
                    metric("High voltage", decimal(source.operatingData().highVoltageVolts(), " V")),
                    metric("Temperature", decimal(source.operatingData().temperatureCelsius(), " °C")),
                    metric("Humidity", decimal(source.operatingData().relativeHumidityPercent(), " %")),
                    metric("Air pressure", decimal(source.operatingData().airPressureHpa(), " hPa")),
                    metric("Device status", source.operatingData().deviceStatus()),
                    metric("Received", epochSeconds(source.operatingData().receivedAtEpochSeconds()))));
        }
        return view(
                gdrmCard(), sections,
                List.of(communication("Serial GDRM worker", source.communication())),
                List.of(action("poll", "Read GDRM now",
                        "Run one identification or data polling cycle.",
                        enablement.isEnabled(com.rgs.center.objects.DeviceCode.GDRM), false))
        );
    }

    private WizardServiceDeviceView simple(DeviceCard card, String note) {
        return view(card, List.of(section("Transport", metric("Details", note))),
                List.of(), List.of());
    }

    private WizardServiceDeviceView view(
            DeviceCard card,
            List<Section> sections,
            List<Communication> communication,
            List<Action> actions
    ) {
        List<Section> all = new ArrayList<>();
        all.add(section("Health",
                metric("Connection", card.connection()),
                metric("Technical status", card.technical()),
                metric("Operation", card.operation()),
                metric("Last response", card.lastResponse()),
                metric("Stale", yesNo(card.stale())),
                metric("Warnings / reasons", card.reasons()),
                metric("Last error", card.lastError())));
        all.addAll(sections);
        return new WizardServiceDeviceView(card, all, communication, actions);
    }

    private DeviceCard gdrmCard() {
        GdrmSnapshot value = gdrm.snapshot();
        boolean enabled = enablement.isEnabled(com.rgs.center.objects.DeviceCode.GDRM);
        String connection = !enabled ? "DISABLED" : value.connected() ? "ONLINE" : "OFFLINE";
        String technical = !enabled ? "DISABLED"
                : !text(value.lastError()).equals("No data") ? "ALERT"
                : value.measurement() != null || value.operatingData() != null ? "READY" : "NO_DATA";
        return new DeviceCard(
                "gdrm", "GDRM", text(value.portName()), "GDRM serial protocol",
                connection, technical, enabled ? "IDLE" : "DISABLED",
                epochSeconds(value.lastInteractionOutEpochSeconds()), text(value.lastError()),
                text(value.lastError()),
                enabled, false, css(enabled, connection, technical, false)
        );
    }

    private static DeviceCard card(DeviceHealth health, String protocol) {
        return new DeviceCard(
                normalize(health.code()), health.name(), text(health.endpoint()), protocol,
                health.connection().name(), health.technical().name(), health.operation().name(),
                time(health.observedAt()), health.reasons().isEmpty()
                        ? "None" : String.join("; ", health.reasons()),
                text(health.lastError()), health.enabled(), health.stale(),
                css(health.enabled(), health.connection().name(), health.technical().name(), health.stale())
        );
    }

    private static String protocol(String code) {
        return switch (normalize(code)) {
            case "camera", "camera-2" -> "HTTP camera snapshot";
            case "lenovo-pc" -> "LAN reachability";
            case "nicole" -> "NICOLE PING/PONG";
            case "boson" -> "Gamma-PRO / BOSON";
            case "roboticmanipulator" -> "Dobot TCP 29999/30006";
            case "barcode-scanner" -> "RS-232 scanner";
            case "scales" -> "RADWAG SI";
            case "shp" -> "SHP binary";
            case "gamma-pro" -> "Gamma-PRO TCP";
            default -> "Device protocol";
        };
    }

    private static Communication communication(String channel, PeripheralWorkerSnapshot value) {
        return new Communication(
                channel, value.state().name(), value.commandQueueDepth(),
                value.receiveBufferBytes(), value.frameQueueDepth(),
                value.bytesReceived(), value.bytesSent(), value.framesReceived(),
                value.framesDropped(), epochMillis(value.lastReceiveEpochMillis()),
                epochMillis(value.lastTransmitEpochMillis()), text(value.lastError())
        );
    }

    private static Section section(String title, Metric... metrics) {
        return new Section(title, Arrays.asList(metrics));
    }

    private static Metric metric(String label, Object value) {
        return new Metric(label, value == null || value.toString().isBlank()
                ? "No data" : value.toString());
    }

    private static Action action(
            String code, String label, String description,
            boolean enabled, boolean dangerous
    ) {
        return new Action(code, label, description, enabled, dangerous);
    }

    private static String css(boolean enabled, String connection, String technical, boolean stale) {
        if (!enabled) return "wizard-device--disabled";
        if (connection.equals("OFFLINE") || technical.equals("ALERT") || stale) {
            return "wizard-device--alert";
        }
        if (connection.equals("CONNECTING") || technical.equals("BUSY")) {
            return "wizard-device--warning";
        }
        if (connection.equals("ONLINE") && technical.equals("READY")) {
            return "wizard-device--ready";
        }
        return "wizard-device--no-data";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "No data" : value;
    }

    private static String time(Instant value) {
        return value == null ? "No data" : TIME.format(value);
    }

    private static String epochSeconds(long value) {
        return value <= 0 ? "No data" : time(Instant.ofEpochSecond(value));
    }

    private static String epochMillis(long value) {
        return value <= 0 ? "No data" : time(Instant.ofEpochMilli(value));
    }

    private static String yesNo(Boolean value) {
        return value == null ? "No data" : value ? "Yes" : "No";
    }

    private static String upDown(Boolean value) {
        return value == null ? "No data" : value ? "Up" : "Down";
    }

    private static String startedStopped(Boolean value) {
        return value == null ? "No data" : value ? "Started" : "Stopped";
    }

    private static String decimal(Double value, String suffix) {
        return value == null ? "No data"
                : String.format(Locale.ROOT, "%.3f%s", value, suffix);
    }

    private static ResponseStatusException notFound(String code) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Unknown service device: " + code);
    }
}
