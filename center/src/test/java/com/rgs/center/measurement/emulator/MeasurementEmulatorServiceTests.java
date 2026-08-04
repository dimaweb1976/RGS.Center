package com.rgs.center.measurement.emulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementEmulatorServiceTests {

    private MeasurementEmulatorService service;

    @BeforeEach
    void setUp() {
        service = new MeasurementEmulatorService();
        service.reset();
    }

    @Test
    void enablesStartOnlyAfterPreparationAndObjectDetection() {
        service.detectObjects();
        assertFalse(service.snapshot().startEnabled());

        advancePreparation();

        MeasurementEmulatorSnapshot snapshot = service.snapshot();
        assertEquals(MeasurementEmulatorPhase.READY_TO_START, snapshot.phase());
        assertTrue(snapshot.startEnabled());
        assertEquals(3, snapshot.objects().size());
    }

    @Test
    void includesLenovoPcInDeviceConnectionChecks() {
        assertTrue(service.snapshot().deviceChecks().stream()
                .anyMatch(check -> check.code().equals("lenovo-pc")
                        && check.label().equals("LenovoPC")));
    }

    @Test
    void completesCycleForEveryDetectedObject() {
        service.detectObjects();
        advancePreparation();
        service.startCycle();

        for (int tick = 0; tick < 27; tick++) {
            service.advance();
        }

        MeasurementEmulatorSnapshot snapshot = service.snapshot();
        assertEquals(MeasurementEmulatorPhase.COMPLETED, snapshot.phase());
        assertEquals(3, snapshot.completedObjects());
        assertTrue(snapshot.objects().stream()
                .allMatch(object -> object.status().equals("ГОТОВ")));
        assertEquals(
                "Gamma-PRO: набрать спектр и дождаться завершения",
                snapshot.cycleSteps().get(6).name()
        );
    }

    @Test
    void raisesAlertAndRecoversAfterEmulatedFailure() {
        service.toggleCheck("boson-ready");
        assertEquals(MeasurementEmulatorPhase.ALERT, service.snapshot().phase());
        assertTrue(service.snapshot().alert().contains("BOSON.Status"));

        service.toggleCheck("boson-ready");

        assertEquals(MeasurementEmulatorPhase.PRECHECK, service.snapshot().phase());
        assertTrue(service.snapshot().alert().isEmpty());
    }

    @Test
    void blocksScenarioWhenLeakageCurrentExceedsLimit() {
        service.toggleLeakageCurrent();

        MeasurementEmulatorSnapshot snapshot = service.snapshot();
        assertEquals(MeasurementEmulatorPhase.ALERT, snapshot.phase());
        assertTrue(snapshot.leakageCurrentMicroamps()
                > snapshot.leakageLimitMicroamps());
    }

    private void advancePreparation() {
        service.advance();
        service.advance();
        service.advance();
    }
}
