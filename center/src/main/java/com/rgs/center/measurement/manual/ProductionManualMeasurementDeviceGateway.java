package com.rgs.center.measurement.manual;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.ManipulatorEquipmentSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.workflow.MeasurementObjectRequest;
import com.rgs.center.measurement.workflow.ProductionMeasurementWorkflowGateway;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Manual operations implemented through the existing shared device services. */
@Component
public class ProductionManualMeasurementDeviceGateway
        implements ManualMeasurementDeviceGateway {

    private static final MeasurementObjectRequest MANUAL_OBJECT =
            new MeasurementObjectRequest(1, 0, 0);

    private final DashboardSnapshotService dashboardService;
    private final ProductionMeasurementWorkflowGateway workflowGateway;
    private final RoboticManipulatorStreamService manipulator;
    private final GammaProService gammaPro;
    private final BosonService boson;
    private final SettingsService settings;
    private final ManualMeasurementConfiguration configuration;

    public ProductionManualMeasurementDeviceGateway(
            DashboardSnapshotService dashboardService,
            ProductionMeasurementWorkflowGateway workflowGateway,
            RoboticManipulatorStreamService manipulator,
            GammaProService gammaPro,
            BosonService boson,
            SettingsService settings,
            ManualMeasurementConfiguration configuration
    ) {
        this.dashboardService = dashboardService;
        this.workflowGateway = workflowGateway;
        this.manipulator = manipulator;
        this.gammaPro = gammaPro;
        this.boson = boson;
        this.settings = settings;
        this.configuration = configuration;
    }

    @Override
    public String scanBarcode() {
        requireReady(DeviceCode.BARCODE_SCANNER);
        return workflowGateway.readBarcode(MANUAL_OBJECT);
    }

    @Override
    public BigDecimal readWeight() {
        requireScalesReadable(health(DeviceCode.SCALES));
        return workflowGateway.readWeight(MANUAL_OBJECT);
    }

    @Override
    public void openHatch() {
        operateHatch(
                configuration.getOpenHatchCommand(),
                ManipulatorEquipmentSnapshot.HatchState.OPEN
        );
    }

    @Override
    public void closeHatch() {
        operateHatch(
                configuration.getCloseHatchCommand(),
                ManipulatorEquipmentSnapshot.HatchState.CLOSED
        );
    }

    @Override
    public void enableHighVoltage() {
        requireReady(DeviceCode.GAMMA_PRO);
        requireReady(DeviceCode.BOSON);
        configuration.validate();
        var result = boson.enableHighVoltage(
                settings.getValue(SettingsService.BOSON_MCA_NAME, String.class),
                settings.getValue(SettingsService.BOSON_CHANNEL_NAME, String.class),
                configuration.getHighVoltage()
        );
        if (!result.commandAccepted()) {
            throw new IllegalStateException("Gamma-PRO did not confirm the HV command");
        }
    }

    @Override
    public void disableHighVoltage() {
        requireReachable(DeviceCode.GAMMA_PRO);
        requireReachable(DeviceCode.BOSON);
        var result = boson.disableHighVoltage(
                settings.getValue(SettingsService.BOSON_MCA_NAME, String.class),
                settings.getValue(SettingsService.BOSON_CHANNEL_NAME, String.class)
        );
        if (!result.commandAccepted()) {
            throw new IllegalStateException("Gamma-PRO did not confirm HV shutdown");
        }
    }

    @Override
    public void verifySpectrumPreconditions() {
        requireReady(DeviceCode.GAMMA_PRO);
        requireReady(DeviceCode.BOSON);
        requireReady(DeviceCode.SHP);
        requireReady(DeviceCode.ROBOTIC_MANIPULATOR);
        var equipment = dashboardService.snapshot().equipment();
        if (equipment.manipulator().hatch().state()
                != ManipulatorEquipmentSnapshot.HatchState.CLOSED) {
            throw new IllegalStateException("Spectrometer hatch is not confirmed closed");
        }
        if (!Boolean.TRUE.equals(equipment.boson().highVoltageRequested())
                || !Boolean.TRUE.equals(equipment.boson().commandAccepted())) {
            throw new IllegalStateException("BOSON high voltage is not confirmed enabled");
        }
    }

    @Override
    public String acquireSpectrum(MeasurementParameters parameters) {
        return workflowGateway.acquireSpectrum(MANUAL_OBJECT, parameters);
    }

    @Override
    public void stopSpectrumAcquisition() {
        if (gammaPro.snapshot().measurementState() == GammaProMeasurementState.RUNNING) {
            gammaPro.stopSpectrumAcquisition();
        }
    }

    private void operateHatch(
            String command,
            ManipulatorEquipmentSnapshot.HatchState expected
    ) {
        requireReady(DeviceCode.ROBOTIC_MANIPULATOR);
        configuration.validate();
        if (command == null || command.isBlank()) {
            throw new IllegalStateException(
                    "Manual " + expected.name().toLowerCase()
                            + " hatch command is not configured"
            );
        }
        manipulator.executeCommandAndAwaitCompletion(
                command,
                configuration.getOperationTimeout()
        );
        ManipulatorEquipmentSnapshot.HatchState actual = dashboardService
                .snapshot().equipment().manipulator().hatch().state();
        if (actual != expected) {
            throw new IllegalStateException(
                    "Hatch command completed, but " + expected
                            + " limit switch is not active (actual: " + actual + ")"
            );
        }
    }

    private void requireReady(DeviceCode device) {
        DeviceHealth health = health(device);
        requireReachable(health);
        if (health.technical() != TechnicalState.READY) {
            throw new IllegalStateException(
                    health.name() + " technical status is " + health.technical()
            );
        }
        if (health.operation() != OperationalState.IDLE
                && health.operation() != OperationalState.COMPLETED) {
            throw new IllegalStateException(
                    health.name() + " operation status is " + health.operation()
            );
        }
        if (health.stale()) {
            throw new IllegalStateException(health.name() + " status is stale");
        }
    }

    /**
     * An unstable scales reading is a valid state before requesting a new value.
     * The command itself performs a fresh protocol exchange and only accepts a
     * stable result, so BUSY must not be treated as a disconnected device.
     */
    static void requireScalesReadable(DeviceHealth health) {
        requireReachable(health);
        if (health.technical() != TechnicalState.READY
                && health.technical() != TechnicalState.BUSY) {
            throw new IllegalStateException(
                    health.name() + " technical status is " + health.technical()
            );
        }
        if (health.operation() != OperationalState.IDLE
                && health.operation() != OperationalState.COMPLETED) {
            throw new IllegalStateException(
                    health.name() + " operation status is " + health.operation()
            );
        }
        if (health.stale()) {
            throw new IllegalStateException(health.name() + " status is stale");
        }
    }

    private void requireReachable(DeviceCode device) {
        requireReachable(health(device));
    }

    private static void requireReachable(DeviceHealth health) {
        if (!health.enabled()) {
            throw new IllegalStateException(health.name() + " is disabled");
        }
        if (health.connection() != ConnectionState.ONLINE) {
            throw new IllegalStateException(
                    health.name() + " connection is " + health.connection()
            );
        }
    }

    private DeviceHealth health(DeviceCode device) {
        return dashboardService.snapshot().equipment().healthSummary().stream()
                .filter(value -> value.code().equals(device.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No dashboard state for " + device.getDisplayName()
                ));
    }
}
