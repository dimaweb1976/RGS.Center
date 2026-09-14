package com.rgs.center.measurement.workflow;

import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.gammapro.GammaProMeasurementRequest;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gammapro.GammaProSnapshot;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.scales.ScalesReading;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesStability;
import com.rgs.center.service.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Production adapter backed by the existing queued device services. */
@Component
public class ProductionMeasurementWorkflowGateway
        implements MeasurementWorkflowDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(
            ProductionMeasurementWorkflowGateway.class
    );
    private static final Set<String> REQUIRED_DEVICES = Set.of(
            DeviceCode.CAMERA.getCode(),
            DeviceCode.NICOLE.getCode(),
            DeviceCode.BOSON.getCode(),
            DeviceCode.ROBOTIC_MANIPULATOR.getCode(),
            DeviceCode.BARCODE_SCANNER.getCode(),
            DeviceCode.SCALES.getCode(),
            DeviceCode.SHP.getCode(),
            DeviceCode.GAMMA_PRO.getCode()
    );

    private final DashboardSnapshotService dashboardService;
    private final RoboticManipulatorStreamService manipulator;
    private final BarcodeScannerService barcodeScanner;
    private final ScalesService scales;
    private final GammaProService gammaPro;
    private final SettingsService settings;
    private final MeasurementWorkflowConfiguration configuration;
    private final ScheduledExecutorService gammaPoller =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "gamma-pro-workflow-poller");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicReference<ScheduledFuture<?>> activeGammaPoll =
            new AtomicReference<>();

    public ProductionMeasurementWorkflowGateway(
            DashboardSnapshotService dashboardService,
            RoboticManipulatorStreamService manipulator,
            BarcodeScannerService barcodeScanner,
            ScalesService scales,
            GammaProService gammaPro,
            SettingsService settings,
            MeasurementWorkflowConfiguration configuration
    ) {
        this.dashboardService = dashboardService;
        this.manipulator = manipulator;
        this.barcodeScanner = barcodeScanner;
        this.scales = scales;
        this.gammaPro = gammaPro;
        this.settings = settings;
        this.configuration = configuration;
    }

    @Override
    public WorkflowPreflightReport preflight(
            List<MeasurementObjectRequest> objects,
            MeasurementParameters parameters
    ) {
        List<String> issues = new ArrayList<>();
        if (objects.isEmpty()) issues.add("No objects are queued");
        if (!configuration.getManipulator().isComplete()) {
            issues.add("Manipulator workflow command templates are not configured");
        }
        dashboardService.snapshot().equipment().healthSummary().stream()
                .filter(health -> REQUIRED_DEVICES.contains(health.code()))
                .forEach(health -> validateHealth(health, issues));
        return new WorkflowPreflightReport(issues);
    }

    @Override
    public void pickObject(MeasurementObjectRequest object) {
        executeMotion(render(
                configuration.getManipulator().getPickCommandTemplate(), object
        ));
    }

    @Override
    public void moveToBarcode(MeasurementObjectRequest object) {
        executeMotion(configuration.getManipulator().getMoveToBarcodeCommand());
    }

    @Override
    public String readBarcode(MeasurementObjectRequest object) {
        return barcodeScanner.readBarcode();
    }

    @Override
    public void moveToScales(MeasurementObjectRequest object) {
        executeMotion(configuration.getManipulator().getMoveToScalesCommand());
    }

    @Override
    public BigDecimal readWeight(MeasurementObjectRequest object) {
        ScalesReading reading = scales.readWeight();
        if (reading.stability() != ScalesStability.STABLE) {
            throw new IllegalStateException("Scales reading is not stable");
        }
        return reading.massGrams();
    }

    @Override
    public void moveToSpectrometer(MeasurementObjectRequest object) {
        executeMotion(configuration.getManipulator().getMoveToSpectrometerCommand());
    }

    @Override
    public String acquireSpectrum(
            MeasurementObjectRequest object,
            MeasurementParameters parameters
    ) {
        int presetSeconds = Math.toIntExact(Math.multiplyExact(
                parameters.measuringTimeMinutes(), 60L
        ));
        GammaProSnapshot started = gammaPro.startSpectrumAcquisition(
                new GammaProMeasurementRequest(
                        settings.getValue(SettingsService.BOSON_MCA_NAME, String.class),
                        settings.getValue(SettingsService.BOSON_CHANNEL_NAME, String.class),
                        presetSeconds
                )
        );
        if (started.measurementState() == GammaProMeasurementState.COMPLETED) {
            return started.lastResponse();
        }

        CompletableFuture<GammaProSnapshot> completion = new CompletableFuture<>();
        long interval = configuration.getGammaPollInterval().toMillis();
        ScheduledFuture<?> poll = gammaPoller.scheduleWithFixedDelay(() -> {
            try {
                GammaProSnapshot current = gammaPro.pollSpectrumAcquisition();
                if (current.measurementState() == GammaProMeasurementState.COMPLETED) {
                    completion.complete(current);
                } else if (current.measurementState() == GammaProMeasurementState.FAILED) {
                    completion.completeExceptionally(new IllegalStateException(
                            "Gamma-PRO spectrum acquisition failed: " + current.lastError()
                    ));
                }
            } catch (RuntimeException exception) {
                completion.completeExceptionally(exception);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        activeGammaPoll.set(poll);
        try {
            GammaProSnapshot completed = completion.get();
            return completed.lastResponse();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Spectrum acquisition interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Spectrum acquisition failed", cause);
        } finally {
            poll.cancel(true);
            activeGammaPoll.compareAndSet(poll, null);
        }
    }

    @Override
    public void removeFromSpectrometer(MeasurementObjectRequest object) {
        executeMotion(configuration.getManipulator().getRemoveFromSpectrometerCommand());
    }

    @Override
    public void returnObject(MeasurementObjectRequest object) {
        executeMotion(render(
                configuration.getManipulator().getReturnCommandTemplate(), object
        ));
    }

    @Override
    public void cancelActiveOperation() {
        ScheduledFuture<?> poll = activeGammaPoll.getAndSet(null);
        if (poll != null) poll.cancel(true);
        try {
            if (gammaPro.snapshot().measurementState() == GammaProMeasurementState.RUNNING) {
                gammaPro.stopSpectrumAcquisition();
            }
        } catch (RuntimeException exception) {
            log.warn("Gamma-PRO cancellation failed: {}", exception.getMessage());
        }
        try {
            manipulator.executeCommand("Stop()", Duration.ofSeconds(3));
        } catch (RuntimeException exception) {
            log.warn("Dobot cancellation failed: {}", exception.getMessage());
        }
    }

    @PreDestroy
    void shutdownPoller() {
        gammaPoller.shutdownNow();
    }

    private void executeMotion(String command) {
        manipulator.executeCommandAndAwaitCompletion(
                requireCommand(command),
                configuration.getOperationTimeout()
        );
    }

    private static void validateHealth(DeviceHealth health, List<String> issues) {
        if (!health.enabled()) {
            issues.add(health.name() + " is disabled");
            return;
        }
        if (health.connection() != ConnectionState.ONLINE) {
            issues.add(health.name() + " connection is " + health.connection());
        }
        if (health.technical() != TechnicalState.READY) {
            issues.add(health.name() + " technical status is " + health.technical());
        }
        if (health.operation() != OperationalState.IDLE
                && health.operation() != OperationalState.COMPLETED) {
            issues.add(health.name() + " operation status is " + health.operation());
        }
        if (health.stale()) {
            issues.add(health.name() + " status is stale");
        }
    }

    private static String render(String template, MeasurementObjectRequest object) {
        return requireCommand(template)
                .replace("{number}", Integer.toString(object.number()))
                .replace("{x}", String.format(Locale.ROOT, "%.3f", object.x()))
                .replace("{y}", String.format(Locale.ROOT, "%.3f", object.y()));
    }

    private static String requireCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalStateException("Manipulator workflow command is not configured");
        }
        return command.trim();
    }
}
