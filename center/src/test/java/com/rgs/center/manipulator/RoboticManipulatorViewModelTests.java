package com.rgs.center.manipulator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoboticManipulatorViewModelTests {

    @Test
    void formatsTelemetryForReadOnlyTable() {
        DobotFeedbackFrame feedback = new DobotFeedbackFrame(
                1L,
                2L,
                5L,
                0L,
                List.of(),
                new CartesianPose(1.25, 2.5, 3.75, 4.0, 5.0, 6.0),
                false,
                true,
                false,
                false,
                17L,
                0xFF
        );
        RoboticManipulatorSnapshot snapshot = new RoboticManipulatorSnapshot(
                true, null, null, null, feedback, 1_000L,
                new ManipulatorAnalogInputSnapshot(
                        true, 1, 127.25, 900L, ""
                ),
                ""
        );
        ManipulatorStatusView status = new ManipulatorStatusView(
                ManipulatorConnectionStatus.ONLINE,
                ManipulatorReadinessStatus.READY,
                SpectrometerHatchStatus.CLOSED,
                List.of()
        );

        RoboticManipulatorViewModel view =
                RoboticManipulatorViewModel.from(snapshot, status);

        assertThat(view.position()).isEqualTo("X: 1.250; Y: 2.500; Z: 3.750");
        assertThat(view.orientation()).isEqualTo("Rx: 4.000; Ry: 5.000; Rz: 6.000");
        assertThat(view.digitalInputs()).isEqualTo("0x0000000000000001");
        assertThat(view.digitalOutputs()).isEqualTo("0x0000000000000002");
        assertThat(view.commandId()).isEqualTo("17");
        assertThat(view.analogInput()).isEqualTo("AI(1): 127.250");
        assertThat(view.hatch()).isEqualTo("Closed");
        assertThat(view.safety()).isEqualTo("0xFF (no blocking fault)");
        assertThat(view.details()).isEqualTo("-");
    }

    @Test
    void usesPlaceholdersBeforeFirstFeedback() {
        ManipulatorStatusView status = new ManipulatorStatusView(
                ManipulatorConnectionStatus.CONNECTING,
                ManipulatorReadinessStatus.NO_DATA,
                SpectrometerHatchStatus.UNKNOWN,
                List.of("Feedback has not been received")
        );

        RoboticManipulatorViewModel view =
                RoboticManipulatorViewModel.from(null, status);

        assertThat(view.robotMode()).isEqualTo("-");
        assertThat(view.hatch()).isEqualTo("Unknown");
        assertThat(view.details()).isEqualTo("Feedback has not been received");
    }
}
