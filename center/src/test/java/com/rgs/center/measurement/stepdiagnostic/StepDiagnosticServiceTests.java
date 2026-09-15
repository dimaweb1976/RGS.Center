package com.rgs.center.measurement.stepdiagnostic;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

class StepDiagnosticServiceTests {

    private final DashboardSnapshotService dashboard = mock(DashboardSnapshotService.class);
    private final ProductionMeasurementOrchestrator orchestrator =
            mock(ProductionMeasurementOrchestrator.class);
    private final BarcodeScannerService scanner = mock(BarcodeScannerService.class);
    private final ScalesService scales = mock(ScalesService.class);
    private final ExecutorService weightExecutor = mock(ExecutorService.class);
    private final CurrentDataService currentData = mock(CurrentDataService.class);
    private final MeasurementExecutionCoordinator coordinator =
            new MeasurementExecutionCoordinator();
    private final StepDiagnosticService service = new StepDiagnosticService(
            dashboard, orchestrator, scanner, scales, currentData, coordinator,
            Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC),
            weightExecutor
    );

    @Test
    void skipsInOrderAndNeverClaimsProductionCompletion() {
        publishHealth(new DeviceHealth(
                DeviceCode.ROBOTIC_MANIPULATOR.getCode(), "Robotic manipulator", "",
                false, ConnectionState.DISABLED, TechnicalState.DISABLED,
                OperationalState.DISABLED, null, false, List.of(), ""
        ));
        StepDiagnosticSnapshot initial = service.createSession(List.of(1, 2), true);
        assertEquals(18, initial.entries().size());
        assertEquals(StepDiagnosticStatus.WAITING, initial.entries().getFirst().status());
        assertEquals(1, initial.focusObjectNumber());
        assertFalse(initial.complete());

        assertThrows(IllegalStateException.class, () -> service.startStep(
                initial.id(), 1, MeasurementWorkflowStep.MOVE_TO_BARCODE
        ));
        StepDiagnosticSnapshot after = service.startStep(
                initial.id(), 1, MeasurementWorkflowStep.PICK_OBJECT
        );
        assertEquals(StepDiagnosticStatus.SKIPPED, after.entries().getFirst().status());
        assertTrue(after.entries().getFirst().explanation().contains("disabled"));
        assertTrue(after.entries().getFirst().explanation().contains("No command sent"));
        assertEquals(1, after.cursor());
        assertEquals(1, after.skippedCount());
        assertThrows(IllegalStateException.class, () -> service.startStep(
                initial.id(), 1, MeasurementWorkflowStep.PICK_OBJECT
        ));
    }

    @Test
    void onlineDeviceStillSkipsUntilCommandIncrementIsImplemented() {
        publishHealth(new DeviceHealth(
                DeviceCode.ROBOTIC_MANIPULATOR.getCode(), "Robotic manipulator", "",
                true, ConnectionState.ONLINE, TechnicalState.READY,
                OperationalState.IDLE, Instant.parse("2026-09-15T09:59:59Z"),
                false, List.of(), ""
        ));
        StepDiagnosticSnapshot initial = service.createSession(List.of(1), false);
        StepDiagnosticSnapshot after = service.startStep(
                initial.id(), 1, MeasurementWorkflowStep.PICK_OBJECT
        );
        assertEquals(StepDiagnosticStatus.SKIPPED, after.entries().getFirst().status());
        assertTrue(after.entries().getFirst().explanation().contains(
                "this step is not connected yet"
        ));
    }

    @Test
    void confirmedOnlineBarcodeScanCompletesOnlyWithFreshShowcaseData() {
        publishHealth(scannerReady());
        when(currentData.getDeviceData(DeviceCode.BARCODE_SCANNER))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 4L))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 5L,
                        CurrentDataService.DATA_AVAILABLE, true,
                        CurrentDataService.LAST_DATA, "SAMPLE-42"));
        when(scanner.startRead(any())).thenReturn(true);
        StepDiagnosticSnapshot session = sessionAtBarcode();
        session = service.startStep(session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, true);
        assertEquals(StepDiagnosticStatus.RUNNING, session.entries().get(2).status());
        assertEquals(MeasurementExecutionCoordinator.Owner.STEP_TEST, coordinator.owner());
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scanner).startRead(callback.capture());
        callback.getValue().run();
        StepDiagnosticSnapshot finished = service.snapshot().orElseThrow();
        assertEquals(StepDiagnosticStatus.COMPLETED, finished.entries().get(2).status());
        assertEquals("SAMPLE-42", finished.entries().get(2).result());
        assertEquals(3, finished.cursor());
        assertEquals(MeasurementExecutionCoordinator.Owner.NONE, coordinator.owner());
    }

    @Test
    void staleBarcodeResultIsErrorThenMayBeExplicitlySkipped() {
        publishHealth(scannerReady());
        when(currentData.getDeviceData(DeviceCode.BARCODE_SCANNER))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 4L))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 4L,
                        CurrentDataService.DATA_AVAILABLE, true,
                        CurrentDataService.LAST_DATA, "OLD"));
        when(scanner.startRead(any())).thenReturn(true);
        StepDiagnosticSnapshot session = sessionAtBarcode();
        service.startStep(session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, true);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(scanner).startRead(callback.capture());
        callback.getValue().run();
        StepDiagnosticSnapshot failed = service.snapshot().orElseThrow();
        assertEquals(StepDiagnosticStatus.ERROR, failed.entries().get(2).status());
        assertEquals(2, failed.cursor());
        assertEquals(MeasurementExecutionCoordinator.Owner.NONE, coordinator.owner());
        assertEquals(3, service.skipErroredStep(session.id(), 1).cursor());
    }

    @Test
    void scannerIsNeverCommandedWithoutPlacementConfirmationOrReadiness() {
        publishHealth(scannerReady());
        StepDiagnosticSnapshot session = sessionAtBarcode();
        assertThrows(IllegalStateException.class, () -> service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, false));
        verify(scanner, never()).startRead(any());
        publishHealth(new DeviceHealth(DeviceCode.BARCODE_SCANNER.getCode(), "Barcode scanner", "COM8",
                true, ConnectionState.ONLINE, TechnicalState.ALERT, OperationalState.IDLE,
                Instant.parse("2026-09-15T09:59:59Z"), false, List.of(), ""));
        assertEquals(StepDiagnosticStatus.ERROR, service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, true)
                .entries().get(2).status());
        verify(scanner, never()).startRead(any());
    }

    @Test
    void offlineScannerIsSkippedWithoutCommand() {
        publishHealth(new DeviceHealth(DeviceCode.BARCODE_SCANNER.getCode(), "Barcode scanner", "COM8",
                true, ConnectionState.OFFLINE, TechnicalState.NO_DATA, OperationalState.IDLE,
                null, true, List.of(), ""));
        StepDiagnosticSnapshot session = sessionAtBarcode();
        StepDiagnosticSnapshot after = service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, true);
        assertEquals(StepDiagnosticStatus.SKIPPED, after.entries().get(2).status());
        assertEquals(3, after.cursor());
        verify(scanner, never()).startRead(any());
    }

    @Test
    void scannerCannotRunWhenMeasurementEquipmentIsReserved() {
        publishHealth(scannerReady());
        coordinator.acquire(MeasurementExecutionCoordinator.Owner.MANUAL);
        StepDiagnosticSnapshot session = sessionAtBarcode();
        assertThrows(IllegalStateException.class, () -> service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_BARCODE, true));
        assertEquals(StepDiagnosticStatus.WAITING,
                service.snapshot().orElseThrow().entries().get(2).status());
        verify(scanner, never()).startRead(any());
    }

    private StepDiagnosticSnapshot sessionAtBarcode() {
        StepDiagnosticSnapshot session = service.createSession(List.of(1), false);
        session = service.startStep(session.id(), 1, MeasurementWorkflowStep.PICK_OBJECT);
        return service.startStep(session.id(), 1, MeasurementWorkflowStep.MOVE_TO_BARCODE);
    }

    private static DeviceHealth scannerReady() {
        return new DeviceHealth(DeviceCode.BARCODE_SCANNER.getCode(), "Barcode scanner", "COM8",
                true, ConnectionState.ONLINE, TechnicalState.READY, OperationalState.IDLE,
                Instant.parse("2026-09-15T09:59:59Z"), false, List.of(), "");
    }

    @Test
    void stableScalesReadingCompletesFromFreshShowcase() {
        publishHealth(scalesHealth(TechnicalState.READY, ConnectionState.ONLINE));
        ScalesReading reading = new ScalesReading(new BigDecimal("0.8"), "g",
                ScalesStability.STABLE, 100L);
        when(scales.readWeight()).thenReturn(reading);
        when(currentData.getDeviceData(DeviceCode.SCALES))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 2L))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 3L,
                        CurrentDataService.DATA_AVAILABLE, true,
                        CurrentDataService.LAST_DATA, reading));
        StepDiagnosticSnapshot session = sessionAtWeight();
        session = service.startStep(session.id(), 1, MeasurementWorkflowStep.READ_WEIGHT, true);
        assertEquals(StepDiagnosticStatus.RUNNING, session.entries().get(4).status());
        assertEquals(MeasurementExecutionCoordinator.Owner.STEP_TEST, coordinator.owner());
        ArgumentCaptor<Runnable> operation = ArgumentCaptor.forClass(Runnable.class);
        verify(weightExecutor).submit(operation.capture());
        operation.getValue().run();
        StepDiagnosticSnapshot result = service.snapshot().orElseThrow();
        assertEquals(StepDiagnosticStatus.COMPLETED, result.entries().get(4).status());
        assertEquals("0.8 g", result.entries().get(4).result());
        assertEquals(5, result.cursor());
        assertEquals(MeasurementExecutionCoordinator.Owner.NONE, coordinator.owner());
    }

    @Test
    void unstableScalesReadingRemainsErroredAndCanBeRetriedOrSkipped() {
        publishHealth(scalesHealth(TechnicalState.BUSY, ConnectionState.ONLINE));
        ScalesReading reading = new ScalesReading(new BigDecimal("0.8"), "g",
                ScalesStability.UNSTABLE, 100L);
        when(scales.readWeight()).thenReturn(reading);
        when(currentData.getDeviceData(DeviceCode.SCALES))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 2L))
                .thenReturn(Map.of(CurrentDataService.DATA_REVISION, 3L,
                        CurrentDataService.DATA_AVAILABLE, true,
                        CurrentDataService.LAST_DATA, reading));
        StepDiagnosticSnapshot session = sessionAtWeight();
        service.startStep(session.id(), 1, MeasurementWorkflowStep.READ_WEIGHT, true);
        ArgumentCaptor<Runnable> operation = ArgumentCaptor.forClass(Runnable.class);
        verify(weightExecutor).submit(operation.capture());
        operation.getValue().run();
        StepDiagnosticSnapshot errored = service.snapshot().orElseThrow();
        assertEquals(StepDiagnosticStatus.ERROR, errored.entries().get(4).status());
        assertEquals(4, errored.cursor());
        assertTrue(errored.entries().get(4).explanation().contains("UNSTABLE"));
        assertEquals(5, service.skipErroredStep(session.id(), 1).cursor());
    }

    @Test
    void scalesAreNotReadWithoutPlacementOrConnection() {
        publishHealth(scalesHealth(TechnicalState.READY, ConnectionState.ONLINE));
        StepDiagnosticSnapshot session = sessionAtWeight();
        assertThrows(IllegalStateException.class, () -> service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_WEIGHT, false));
        verify(scales, never()).readWeight();
        publishHealth(scalesHealth(TechnicalState.NO_DATA, ConnectionState.OFFLINE));
        assertEquals(StepDiagnosticStatus.SKIPPED, service.startStep(
                session.id(), 1, MeasurementWorkflowStep.READ_WEIGHT, true)
                .entries().get(4).status());
        verify(scales, never()).readWeight();
    }

    private StepDiagnosticSnapshot sessionAtWeight() {
        StepDiagnosticSnapshot session = service.createSession(List.of(1), false);
        for (MeasurementWorkflowStep step : List.of(
                MeasurementWorkflowStep.PICK_OBJECT,
                MeasurementWorkflowStep.MOVE_TO_BARCODE,
                MeasurementWorkflowStep.READ_BARCODE,
                MeasurementWorkflowStep.MOVE_TO_SCALES)) {
            session = service.startStep(session.id(), 1, step);
        }
        return session;
    }

    private static DeviceHealth scalesHealth(TechnicalState technical, ConnectionState connection) {
        return new DeviceHealth(DeviceCode.SCALES.getCode(), "Scales", "COM7",
                true, connection, technical, OperationalState.IDLE,
                connection == ConnectionState.ONLINE
                        ? Instant.parse("2026-09-15T09:59:59Z") : null,
                connection != ConnectionState.ONLINE, List.of(), "");
    }

    @Test
    void rejectsActionsWhileProductionOwnsWorkflow() {
        when(orchestrator.isRunActive()).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> service.createSession(List.of(1), false));
    }

    @Test
    void oldSessionCannotContinueAfterItIsReplaced() {
        StepDiagnosticSnapshot old = service.createSession(List.of(1), false);
        StepDiagnosticSnapshot replacement = service.createSession(List.of(1), false);
        assertThrows(IllegalStateException.class, () -> service.startStep(
                old.id(), 1, MeasurementWorkflowStep.PICK_OBJECT
        ));
        assertEquals(replacement.id(), service.snapshot().orElseThrow().id());
    }

    @Test
    void movesToNextObjectOnlyAfterAllNineStepsAndMarksDiagnosticComplete() {
        publishHealth(new DeviceHealth(
                DeviceCode.ROBOTIC_MANIPULATOR.getCode(), "Robotic manipulator", "",
                false, ConnectionState.DISABLED, TechnicalState.DISABLED,
                OperationalState.DISABLED, null, false, List.of(), ""
        ));
        StepDiagnosticSnapshot session = service.createSession(List.of(1, 2), false);
        List<MeasurementWorkflowStep> cycle = Arrays.stream(MeasurementWorkflowStep.values())
                .filter(step -> step != MeasurementWorkflowStep.PRECHECK).toList();
        for (MeasurementWorkflowStep step : cycle) {
            session = service.startStep(session.id(), 1, step);
        }
        assertEquals(2, session.focusObjectNumber());
        assertEquals(9, session.cursor());
        assertFalse(session.complete());
        for (MeasurementWorkflowStep step : cycle) {
            session = service.startStep(session.id(), 2, step);
        }
        assertTrue(session.complete());
        assertEquals(18, session.skippedCount());
        assertThrows(IllegalStateException.class, () -> service.startStep(
                service.snapshot().orElseThrow().id(), 2, MeasurementWorkflowStep.RETURN_OBJECT
        ));
    }

    private void publishHealth(DeviceHealth health) {
        DashboardSnapshot snapshot = mock(DashboardSnapshot.class);
        EquipmentSnapshot equipment = mock(EquipmentSnapshot.class);
        when(dashboard.snapshot()).thenReturn(snapshot);
        when(snapshot.equipment()).thenReturn(equipment);
        when(equipment.healthSummary()).thenReturn(List.of(health));
    }
}
