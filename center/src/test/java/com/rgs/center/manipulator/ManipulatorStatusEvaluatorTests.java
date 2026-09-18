package com.rgs.center.manipulator;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManipulatorStatusEvaluatorTests {

    private static final long NOW = 10_000L;

    private SettingsService settings;
    private ManipulatorStatusEvaluator evaluator;

    @BeforeEach
    void setUp() {
        settings = mock(SettingsService.class);
        when(settings.getValue(
                SettingsService.MANIPULATOR_TELEMETRY_STALE_MILLIS,
                Long.class
        )).thenReturn(2_000L);
        when(settings.getValue(
                SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT,
                Long.class
        )).thenReturn(0L);
        when(settings.getValue(
                SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT,
                Long.class
        )).thenReturn(1L);
        evaluator = new ManipulatorStatusEvaluator(
                settings,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void disabledDeviceDoesNotRequireTelemetry() {
        ManipulatorStatusView status = evaluator.evaluate(false, null);

        assertThat(status.connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.DISABLED);
        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.DISABLED);
        assertThat(status.hatchStatus())
                .isEqualTo(SpectrometerHatchStatus.UNKNOWN);
    }

    /*
    @Test
    void feedbackOnlyIgnoresFailedDashboardButRequiresFreshFeedback() {
        var initial = snapshot(feedback(1L, false, true, false, false, 0xFF), NOW, "");
        var only = new RoboticManipulatorSnapshot(true, worker(PeripheralWorkerState.FAILED),
                initial.feedbackChannel(), null, initial.latestFeedback(), NOW,
                initial.hatchAnalogInput(), "", true);
        var status = evaluator.evaluate(true, only);
        assertThat(status.connectionStatus()).isEqualTo(ManipulatorConnectionStatus.ONLINE);
        assertThat(status.readiness()).isEqualTo(ManipulatorReadinessStatus.READY);
        var stale = new RoboticManipulatorSnapshot(true, only.commandChannel(), only.feedbackChannel(),
                null, only.latestFeedback(), 7_000L, only.hatchAnalogInput(), "", true);
        assertThat(evaluator.evaluate(true, stale).connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.OFFLINE);
        var waiting = new RoboticManipulatorSnapshot(true, only.commandChannel(), only.feedbackChannel(),
                null, null, 0L, only.hatchAnalogInput(), "", true);
        assertThat(evaluator.evaluate(true, waiting).connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.CONNECTING);
    }
    */

    @Test
    void runningChannelsWithoutFeedbackAreConnectingWithNoData() {
        ManipulatorStatusView status = evaluator.evaluate(
                true,
                snapshot(null, 0L, "")
        );

        assertThat(status.connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.CONNECTING);
        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.NO_DATA);
    }

    @Test
    void freshIdleFeedbackAndClosedHatchAreReady() {
        ManipulatorStatusView status = evaluator.evaluate(
                true,
                snapshot(feedback(1L, false, true, false, false, 0xFF), NOW, "")
        );

        assertThat(status.connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.ONLINE);
        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.READY);
        assertThat(status.hatchStatus())
                .isEqualTo(SpectrometerHatchStatus.CLOSED);
        assertThat(status.reasons()).isEmpty();
    }

    @Test
    void activeMotionIsBusy() {
        ManipulatorStatusView status = evaluator.evaluate(
                true,
                snapshot(feedback(2L, true, true, false, false, 0xFF), NOW, "")
        );

        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.BUSY);
        assertThat(status.hatchStatus())
                .isEqualTo(SpectrometerHatchStatus.OPEN);
    }

    @Test
    void staleFeedbackIsOfflineAndAlert() {
        ManipulatorStatusView status = evaluator.evaluate(
                true,
                snapshot(feedback(0L, false, true, false, false, 0xFF), 7_000L, "")
        );

        assertThat(status.connectionStatus())
                .isEqualTo(ManipulatorConnectionStatus.OFFLINE);
        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.ALERT);
        assertThat(status.reasons()).contains("Telemetry is stale");
    }

    @Test
    void invalidHatchErrorAndSafetyStateAreReportedAsAlert() {
        ManipulatorStatusView status = evaluator.evaluate(
                true,
                snapshot(
                        feedback(3L, false, true, false, true, 0x7C),
                        NOW,
                        "Dashboard failure"
                )
        );

        assertThat(status.readiness())
                .isEqualTo(ManipulatorReadinessStatus.ALERT);
        assertThat(status.hatchStatus())
                .isEqualTo(SpectrometerHatchStatus.INVALID);
        assertThat(status.reasons()).contains(
                "Robot feedback reports an error",
                "Emergency stop",
                "Protective stop",
                "Both hatch limit switches are active",
                "Dashboard failure"
        );
    }

    private static RoboticManipulatorSnapshot snapshot(
            DobotFeedbackFrame feedback,
            long receivedAt,
            String error
    ) {
        return new RoboticManipulatorSnapshot(
                true,
                worker(PeripheralWorkerState.RUNNING),
                worker(PeripheralWorkerState.RUNNING),
                null,
                feedback,
                receivedAt,
                new ManipulatorAnalogInputSnapshot(
                        false, 1, null, 0L, ""
                ),
                error
        );
    }

    private static PeripheralWorkerSnapshot worker(PeripheralWorkerState state) {
        return new PeripheralWorkerSnapshot(
                "manipulator-test", state, 0, 0, 0,
                0L, 0L, 0L, 0L, 0L, 0L, ""
        );
    }

    private static DobotFeedbackFrame feedback(
            long digitalInputs,
            boolean running,
            boolean enabled,
            boolean queued,
            boolean error,
            int safetyState
    ) {
        return new DobotFeedbackFrame(
                digitalInputs,
                4L,
                5L,
                123L,
                List.of(1.0, 2.0),
                new CartesianPose(10.0, 20.0, 30.0, 1.0, 2.0, 3.0),
                queued,
                enabled,
                running,
                error,
                42L,
                safetyState
        );
    }
}
