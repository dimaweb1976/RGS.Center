package com.rgs.center.measurement.stepdiagnostic;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.scales.ScalesReading;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesStability;
import com.rgs.center.service.CurrentDataService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent step test. Confirmed barcode and scales reads may operate a
 * device; remaining cycle steps are explicitly recorded as skipped.
 */
@Service
public class StepDiagnosticService {

    private final DashboardSnapshotService dashboardService;
    private final ProductionMeasurementOrchestrator orchestrator;
    private final BarcodeScannerService barcodeScanner;
    private final ScalesService scales;
    private final CurrentDataService currentData;
    private final MeasurementExecutionCoordinator executionCoordinator;
    private final Clock clock;
    private final ExecutorService weightExecutor;
    private StepDiagnosticSnapshot current;

    @Autowired
    public StepDiagnosticService(
            DashboardSnapshotService dashboardService,
            ProductionMeasurementOrchestrator orchestrator,
            BarcodeScannerService barcodeScanner,
            ScalesService scales,
            CurrentDataService currentData,
            MeasurementExecutionCoordinator executionCoordinator
    ) {
        this(dashboardService, orchestrator, barcodeScanner, scales, currentData,
                executionCoordinator, Clock.systemUTC(),
                Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(task, "step-diagnostic-weight");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    StepDiagnosticService(
            DashboardSnapshotService dashboardService,
            ProductionMeasurementOrchestrator orchestrator,
            BarcodeScannerService barcodeScanner,
            ScalesService scales,
            CurrentDataService currentData,
            MeasurementExecutionCoordinator executionCoordinator,
            Clock clock,
            ExecutorService weightExecutor
    ) {
        this.dashboardService = Objects.requireNonNull(dashboardService);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.barcodeScanner = Objects.requireNonNull(barcodeScanner);
        this.scales = Objects.requireNonNull(scales);
        this.currentData = Objects.requireNonNull(currentData);
        this.executionCoordinator = Objects.requireNonNull(executionCoordinator);
        this.clock = Objects.requireNonNull(clock);
        this.weightExecutor = Objects.requireNonNull(weightExecutor);
    }

    public synchronized StepDiagnosticSnapshot createSession(
            List<Integer> objectNumbers,
            boolean cameraRecognized
    ) {
        rejectActiveProduction();
        if (current != null && current.running()) {
            throw new IllegalStateException("Wait for the active diagnostic read to finish");
        }
        if (objectNumbers == null || objectNumbers.isEmpty()
                || objectNumbers.stream().anyMatch(number -> number == null || number <= 0)
                || objectNumbers.stream().distinct().count() != objectNumbers.size()) {
            throw new IllegalArgumentException("Distinct positive object numbers are required");
        }
        List<StepDiagnosticEntry> entries = new ArrayList<>();
        for (int number : objectNumbers) {
            for (MeasurementWorkflowStep step : Arrays.stream(MeasurementWorkflowStep.values())
                    .filter(value -> value != MeasurementWorkflowStep.PRECHECK).toList()) {
                entries.add(new StepDiagnosticEntry(
                        entries.size(), number, step, StepDiagnosticStatus.WAITING, "", "", null
                ));
            }
        }
        current = new StepDiagnosticSnapshot(
                UUID.randomUUID(), clock.instant(), cameraRecognized,
                objectNumbers, entries, 0
        );
        return current;
    }

    public synchronized StepDiagnosticSnapshot startStep(
            UUID sessionId,
            int objectNumber,
            MeasurementWorkflowStep step,
            boolean samplePositionConfirmed
    ) {
        rejectActiveProduction();
        if (current == null || !current.id().equals(sessionId)) {
            throw new IllegalStateException("Diagnostic session does not exist or was replaced");
        }
        if (current.complete()) {
            throw new IllegalStateException("Diagnostic session is already complete");
        }
        StepDiagnosticEntry expected = current.entries().get(current.cursor());
        if (expected.objectNumber() != objectNumber || expected.step() != step
                || current.running()) {
            throw new IllegalStateException("Start the next waiting diagnostic step only");
        }
        DeviceCode device = deviceFor(step);
        DeviceHealth health = dashboardService.snapshot().equipment().healthSummary().stream()
                .filter(value -> value.code().equals(device.getCode()))
                .findFirst().orElse(null);
        if (step == MeasurementWorkflowStep.READ_BARCODE
                && health != null && health.enabled()
                && health.connection() == ConnectionState.ONLINE) {
            if (health.stale() || health.technical() != TechnicalState.READY) {
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        "Barcode scanner is connected but not technically ready; no command sent",
                        "", false);
                return current;
            }
            return startBarcodeScan(expected, samplePositionConfirmed);
        }
        if (step == MeasurementWorkflowStep.READ_WEIGHT
                && health != null && health.enabled()
                && health.connection() == ConnectionState.ONLINE) {
            if (health.stale() || (health.technical() != TechnicalState.READY
                    && health.technical() != TechnicalState.BUSY)) {
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        "Scales are connected but have no usable state; no command sent",
                        "", false);
                return current;
            }
            return startWeightRead(expected, samplePositionConfirmed);
        }
        String explanation = skipExplanation(device, health);
        List<StepDiagnosticEntry> updated = new ArrayList<>(current.entries());
        Instant now = clock.instant();
        updated.set(current.cursor(), new StepDiagnosticEntry(
                expected.sequence(), objectNumber, step,
                StepDiagnosticStatus.SKIPPED, explanation, "", now
        ));
        current = new StepDiagnosticSnapshot(
                current.id(), current.createdAt(), current.cameraRecognized(),
                current.objectNumbers(), updated, current.cursor() + 1
        );
        return current;
    }

    public synchronized StepDiagnosticSnapshot startStep(
            UUID sessionId,
            int objectNumber,
            MeasurementWorkflowStep step
    ) {
        return startStep(sessionId, objectNumber, step, false);
    }

    public synchronized StepDiagnosticSnapshot skipErroredStep(
            UUID sessionId,
            int objectNumber
    ) {
        if (current == null || !current.id().equals(sessionId) || current.complete()) {
            throw new IllegalStateException("Diagnostic session does not exist or is complete");
        }
        StepDiagnosticEntry entry = current.entries().get(current.cursor());
        if (entry.objectNumber() != objectNumber || entry.status() != StepDiagnosticStatus.ERROR) {
            throw new IllegalStateException("Only the current errored step may be skipped");
        }
        replaceCurrent(StepDiagnosticStatus.SKIPPED,
                "Operator skipped after error: " + entry.explanation(), "", true);
        return current;
    }

    public synchronized boolean cancelActiveScan(UUID sessionId) {
        if (current == null || !current.id().equals(sessionId) || !current.running()
                || current.entries().get(current.cursor()).step()
                != MeasurementWorkflowStep.READ_BARCODE) {
            return false;
        }
        return barcodeScanner.cancelRead();
    }

    public synchronized Optional<StepDiagnosticSnapshot> snapshot() {
        return Optional.ofNullable(current);
    }

    private StepDiagnosticSnapshot startBarcodeScan(
            StepDiagnosticEntry expected,
            boolean samplePositionConfirmed
    ) {
        if (!samplePositionConfirmed) {
            throw new IllegalStateException(
                    "Confirm that the sample is manually positioned at the scanner"
            );
        }
        long before = revision(currentData.getDeviceData(DeviceCode.BARCODE_SCANNER));
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.STEP_TEST);
        UUID sessionId = current.id();
        int sequence = expected.sequence();
        replaceCurrent(StepDiagnosticStatus.RUNNING,
                "Reading barcode; sample position confirmed by operator", "", false);
        try {
            boolean accepted = barcodeScanner.startRead(
                    () -> finishBarcodeScan(sessionId, sequence, before)
            );
            if (!accepted) {
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        "Barcode scanner is already busy", "", false);
                executionCoordinator.release(MeasurementExecutionCoordinator.Owner.STEP_TEST);
            }
        } catch (RuntimeException exception) {
            replaceCurrent(StepDiagnosticStatus.ERROR,
                    "Barcode scan could not start: " + message(exception), "", false);
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.STEP_TEST);
        }
        return current;
    }

    private StepDiagnosticSnapshot startWeightRead(
            StepDiagnosticEntry expected,
            boolean samplePositionConfirmed
    ) {
        if (!samplePositionConfirmed) {
            throw new IllegalStateException(
                    "Confirm that the sample is manually positioned on the scales"
            );
        }
        long before = revision(currentData.getDeviceData(DeviceCode.SCALES));
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.STEP_TEST);
        UUID sessionId = current.id();
        int sequence = expected.sequence();
        replaceCurrent(StepDiagnosticStatus.RUNNING,
                "Requesting one scales reading; sample position confirmed by operator",
                "", false);
        try {
            weightExecutor.submit(() -> {
                try {
                    ScalesReading reading = scales.readWeight();
                    finishWeightRead(sessionId, sequence, before, reading);
                } catch (RuntimeException exception) {
                    failWeightRead(sessionId, sequence, exception);
                } finally {
                    executionCoordinator.release(MeasurementExecutionCoordinator.Owner.STEP_TEST);
                }
            });
        } catch (RuntimeException exception) {
            replaceCurrent(StepDiagnosticStatus.ERROR,
                    "Scales read could not start: " + message(exception), "", false);
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.STEP_TEST);
        }
        return current;
    }

    private synchronized void finishWeightRead(
            UUID sessionId, int sequence, long before, ScalesReading reading
    ) {
        if (!isCurrentRunning(sessionId, sequence)) return;
        try {
            var values = currentData.getDeviceData(DeviceCode.SCALES);
            long after = revision(values);
            if (after <= before || !Boolean.TRUE.equals(values.get(CurrentDataService.DATA_AVAILABLE))
                    || !reading.equals(values.get(CurrentDataService.LAST_DATA))) {
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        "No matching fresh scales reading in the current-data showcase",
                        "", false);
            } else if (reading.stability() != ScalesStability.STABLE) {
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        "Scales reading is " + reading.stability() + "; retry when stable",
                        reading.mass().toPlainString() + " " + reading.unit(), false);
            } else {
                replaceCurrent(StepDiagnosticStatus.COMPLETED,
                        "Stable scales reading from current-data showcase, revision " + after,
                        reading.massGrams().toPlainString() + " g", true);
            }
        } catch (RuntimeException exception) {
            replaceCurrent(StepDiagnosticStatus.ERROR,
                    "Scales showcase could not be read: " + message(exception), "", false);
        }
    }

    private synchronized void failWeightRead(UUID sessionId, int sequence,
                                             RuntimeException exception) {
        if (isCurrentRunning(sessionId, sequence)) {
            replaceCurrent(StepDiagnosticStatus.ERROR,
                    "Scales read failed: " + message(exception), "", false);
        }
    }

    private boolean isCurrentRunning(UUID sessionId, int sequence) {
        return current != null && current.id().equals(sessionId) && !current.complete()
                && current.cursor() == sequence
                && current.entries().get(sequence).status() == StepDiagnosticStatus.RUNNING;
    }

    @PreDestroy
    void closeWeightExecutor() {
        weightExecutor.shutdownNow();
    }

    private synchronized void finishBarcodeScan(UUID sessionId, int sequence, long before) {
        try {
            finishBarcodeScanResult(sessionId, sequence, before);
        } finally {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.STEP_TEST);
        }
    }

    private void finishBarcodeScanResult(UUID sessionId, int sequence, long before) {
        if (current == null || !current.id().equals(sessionId) || current.complete()
                || current.cursor() != sequence
                || current.entries().get(sequence).status() != StepDiagnosticStatus.RUNNING) {
            return;
        }
        try {
            var values = currentData.getDeviceData(DeviceCode.BARCODE_SCANNER);
            long after = revision(values);
            Object value = values.get(CurrentDataService.LAST_DATA);
            if (after > before && Boolean.TRUE.equals(values.get(CurrentDataService.DATA_AVAILABLE))
                    && value instanceof String barcode && !barcode.isBlank()) {
                replaceCurrent(StepDiagnosticStatus.COMPLETED,
                        "New barcode read from current-data showcase, revision " + after,
                        barcode, true);
            } else {
                Object error = values.get(CurrentDataService.LAST_ERROR);
                replaceCurrent(StepDiagnosticStatus.ERROR,
                        error instanceof String text && !text.isBlank() ? text
                                : "No fresh barcode was published in the current-data showcase",
                        "", false);
            }
        } catch (RuntimeException exception) {
            replaceCurrent(StepDiagnosticStatus.ERROR,
                    "Barcode showcase could not be read: " + message(exception), "", false);
        }
    }

    private void replaceCurrent(
            StepDiagnosticStatus status,
            String explanation,
            String result,
            boolean advance
    ) {
        StepDiagnosticEntry old = current.entries().get(current.cursor());
        List<StepDiagnosticEntry> updated = new ArrayList<>(current.entries());
        updated.set(current.cursor(), new StepDiagnosticEntry(
                old.sequence(), old.objectNumber(), old.step(), status,
                explanation, result, clock.instant()
        ));
        current = new StepDiagnosticSnapshot(
                current.id(), current.createdAt(), current.cameraRecognized(),
                current.objectNumbers(), updated, current.cursor() + (advance ? 1 : 0)
        );
    }

    private static long revision(java.util.Map<String, Object> values) {
        Object value = values.get(CurrentDataService.DATA_REVISION);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private void rejectActiveProduction() {
        if (orchestrator.isRunActive()) {
            throw new IllegalStateException(
                    "Step testing is unavailable while a production run is active"
            );
        }
    }

    private static DeviceCode deviceFor(MeasurementWorkflowStep step) {
        return switch (step) {
            case PICK_OBJECT, MOVE_TO_BARCODE, MOVE_TO_SCALES,
                    MOVE_TO_SPECTROMETER, REMOVE_FROM_SPECTROMETER,
                    RETURN_OBJECT -> DeviceCode.ROBOTIC_MANIPULATOR;
            case READ_BARCODE -> DeviceCode.BARCODE_SCANNER;
            case READ_WEIGHT -> DeviceCode.SCALES;
            case ACQUIRE_SPECTRUM -> DeviceCode.GAMMA_PRO;
            case PRECHECK -> throw new IllegalArgumentException("Preflight is not a cycle step");
        };
    }

    private static String skipExplanation(DeviceCode device, DeviceHealth health) {
        if (health == null) {
            return device.getDisplayName() + " has no status; step skipped. No command sent.";
        }
        if (!health.enabled() || health.connection() == ConnectionState.DISABLED) {
            return device.getDisplayName() + " is disabled; step skipped. No command sent.";
        }
        if (health.connection() != ConnectionState.ONLINE) {
            return device.getDisplayName() + " connection is " + health.connection()
                    + "; step skipped. No command sent.";
        }
        return device.getDisplayName()
                + " is online, but this step is not connected yet. "
                + "Step skipped; no command sent.";
    }
}
