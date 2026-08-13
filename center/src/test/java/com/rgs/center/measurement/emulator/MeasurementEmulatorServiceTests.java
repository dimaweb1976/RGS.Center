package com.rgs.center.measurement.emulator;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.camera.DetectedBottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementEmulatorServiceTests {

    private MeasurementEmulatorService service;

    @BeforeEach
    void setUp() {
        service = new MeasurementEmulatorService(new FakeCameraImaging());
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

    private static final class FakeCameraImaging
            implements CameraImagingOperations {

        private CameraImagingSnapshot snapshot = empty();

        @Override
        public CameraImagingSnapshot capture() {
            snapshot = new CameraImagingSnapshot(
                    true, 1, false, "Тестовый снимок загружен",
                    "", "", List.of()
            );
            return snapshot;
        }

        @Override
        public CameraImagingSnapshot recognize() {
            List<DetectedBottle> bottles = List.of(
                    bottle(1, 100, 100),
                    bottle(2, 200, 100),
                    bottle(3, 300, 100)
            );
            snapshot = new CameraImagingSnapshot(
                    true, 2, true, "Найдено бутылок: 3",
                    "C:\\camera\\bottles_test.jpeg",
                    "C:\\camera\\bottles_detected_test.jpeg",
                    bottles
            );
            return snapshot;
        }

        @Override
        public CameraImagingSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public Optional<CameraImagePayload> currentImage() {
            return Optional.empty();
        }

        @Override
        public void reset() {
            snapshot = empty();
        }

        private static CameraImagingSnapshot empty() {
            return new CameraImagingSnapshot(
                    false, 0, false, "Снимок ещё не сделан.",
                    "", "", List.of()
            );
        }

        private static DetectedBottle bottle(int number, double x, double y) {
            return new DetectedBottle(number, x, y, 60, 62, 61, 0.85);
        }
    }
}
