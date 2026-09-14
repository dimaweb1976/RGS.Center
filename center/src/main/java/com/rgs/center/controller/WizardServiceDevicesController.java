package com.rgs.center.controller;

import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gdrm.GdrmService;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.SettingsService;
import com.rgs.center.shp.ShpService;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardServiceDeviceView;
import com.rgs.center.web.wizard.WizardServiceDeviceViewFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Locale;

/** Unified service workspace backed by existing device services and snapshots. */
@Controller
@RequestMapping("/wizard/devices")
public class WizardServiceDevicesController {

    private static final Logger log = LoggerFactory.getLogger(
            WizardServiceDevicesController.class
    );

    private final DashboardSnapshotService dashboard;
    private final WizardHomeViewFactory homeViewFactory;
    private final WizardServiceDeviceViewFactory deviceViewFactory;
    private final MeasurementExecutionCoordinator executionCoordinator;
    private final CameraImagingOperations camera;
    private final NicoleService nicole;
    private final ShpService shp;
    private final ScalesService scales;
    private final BarcodeScannerService barcode;
    private final GammaProService gammaPro;
    private final GdrmService gdrm;
    private final BosonService boson;
    private final SettingsService settings;

    public WizardServiceDevicesController(
            DashboardSnapshotService dashboard,
            WizardHomeViewFactory homeViewFactory,
            WizardServiceDeviceViewFactory deviceViewFactory,
            MeasurementExecutionCoordinator executionCoordinator,
            CameraImagingOperations camera,
            NicoleService nicole,
            ShpService shp,
            ScalesService scales,
            BarcodeScannerService barcode,
            GammaProService gammaPro,
            GdrmService gdrm,
            BosonService boson,
            SettingsService settings
    ) {
        this.dashboard = dashboard;
        this.homeViewFactory = homeViewFactory;
        this.deviceViewFactory = deviceViewFactory;
        this.executionCoordinator = executionCoordinator;
        this.camera = camera;
        this.nicole = nicole;
        this.shp = shp;
        this.scales = scales;
        this.barcode = barcode;
        this.gammaPro = gammaPro;
        this.gdrm = gdrm;
        this.boson = boson;
        this.settings = settings;
    }

    @GetMapping
    public String overview(Model model) {
        DashboardSnapshot snapshot = addHeader(model, "SERVICE - DEVICES");
        model.addAttribute("serviceOverview", deviceViewFactory.overview(snapshot));
        return "wizard/service-devices";
    }

    @GetMapping("/status")
    public String overviewStatus(Model model) {
        DashboardSnapshot snapshot = dashboard.snapshot();
        model.addAttribute("serviceOverview", deviceViewFactory.overview(snapshot));
        return overviewFragment();
    }

    @GetMapping("/{deviceCode}")
    public String detail(@PathVariable String deviceCode, Model model) {
        DashboardSnapshot snapshot = addHeader(model, "SERVICE - DEVICE");
        model.addAttribute("serviceDevice", deviceViewFactory.detail(deviceCode, snapshot));
        return "wizard/service-device-detail";
    }

    @GetMapping("/{deviceCode}/status")
    public String detailStatus(@PathVariable String deviceCode, Model model) {
        model.addAttribute(
                "serviceDevice",
                deviceViewFactory.detail(deviceCode, dashboard.snapshot())
        );
        return detailFragment();
    }

    @PostMapping("/{deviceCode}/actions/{actionCode}")
    public String action(
            @PathVariable String deviceCode,
            @PathVariable String actionCode,
            Model model
    ) {
        try {
            executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.SERVICE);
            try {
                validateActionAvailable(deviceCode, actionCode);
                model.addAttribute(
                        "serviceActionMessage",
                        execute(deviceCode, actionCode)
                );
            } finally {
                executionCoordinator.release(MeasurementExecutionCoordinator.Owner.SERVICE);
            }
        } catch (RuntimeException exception) {
            String message = errorMessage(exception);
            log.warn(
                    "Service device action failed: device={}, action={}, error={}",
                    deviceCode, actionCode, message
            );
            model.addAttribute("serviceActionMessage", message);
            model.addAttribute("serviceActionError", true);
        }
        model.addAttribute(
                "serviceDevice",
                deviceViewFactory.detail(deviceCode, dashboard.snapshot())
        );
        return detailFragment();
    }

    private void validateActionAvailable(String deviceCode, String actionCode) {
        WizardServiceDeviceView device = deviceViewFactory.detail(
                deviceCode, dashboard.snapshot()
        );
        WizardServiceDeviceView.Action action = device.actions().stream()
                .filter(value -> normalize(value.code()).equals(normalize(actionCode)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported service action " + actionCode
                                + " for device " + deviceCode
                ));
        if (!action.enabled()) {
            throw new IllegalStateException(
                    action.label() + " is unavailable in the current device state"
            );
        }
    }

    private String execute(String requestedDevice, String requestedAction) {
        String device = normalize(requestedDevice);
        String action = normalize(requestedAction);
        return switch (device + ":" + action) {
            case "camera:capture" -> "Camera: " + camera.capture().message();
            case "nicole:ping" -> {
                nicole.poll();
                yield "NICOLE PING/PONG cycle requested";
            }
            case "shp:poll" -> {
                shp.poll();
                yield "SHP polling cycle requested";
            }
            case "scales:read" -> {
                var reading = scales.readWeight();
                yield "Scales response: " + reading.mass() + " "
                        + reading.unit() + " (" + reading.stability() + ")";
            }
            case "barcode-scanner:scan" -> {
                if (!barcode.startRead()) {
                    throw new IllegalStateException("Barcode scan is already active");
                }
                yield "Barcode scan started";
            }
            case "barcode-scanner:cancel" -> {
                if (!barcode.cancelRead()) {
                    throw new IllegalStateException("There is no active barcode scan");
                }
                yield "Barcode scan cancellation requested";
            }
            case "gamma-pro:heartbeat" -> {
                gammaPro.pollGammaPro();
                yield "Gamma-PRO application heartbeat requested";
            }
            case "gdrm:poll" -> {
                gdrm.poll();
                yield "GDRM polling cycle requested";
            }
            case "boson:select-tract" -> {
                boson.selectTract(mcaName(), channelName());
                yield "Configured BOSON tract selected";
            }
            case "boson:hv-on" -> {
                boson.enableHighVoltage(
                        mcaName(), channelName(), BosonService.MANUAL_HIGH_VOLTAGE
                );
                yield "BOSON HV-on request accepted by Gamma-PRO";
            }
            case "boson:hv-off" -> {
                boson.disableHighVoltage(mcaName(), channelName());
                yield "BOSON HV-off request accepted by Gamma-PRO";
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported service action " + requestedAction
                            + " for device " + requestedDevice
            );
        };
    }

    private DashboardSnapshot addHeader(Model model, String title) {
        DashboardSnapshot snapshot = dashboard.snapshot();
        model.addAttribute("wizardTitle", title);
        model.addAttribute("wizardView", homeViewFactory.create(snapshot));
        return snapshot;
    }

    private String mcaName() {
        return settings.getValue(SettingsService.BOSON_MCA_NAME, String.class);
    }

    private String channelName() {
        return settings.getValue(SettingsService.BOSON_CHANNEL_NAME, String.class);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static String overviewFragment() {
        return "wizard/fragments/service-device-list :: serviceContent";
    }

    private static String detailFragment() {
        return "wizard/fragments/service-device-detail :: serviceContent";
    }
}
