package com.rgs.center.api.manipulator;

import com.rgs.center.manipulator.DobotFeedbackFrame;
import com.rgs.center.manipulator.DobotSafetyState;
import com.rgs.center.manipulator.ManipulatorAnalogInputSnapshot;
import com.rgs.center.manipulator.ManipulatorStatusEvaluator;
import com.rgs.center.manipulator.ManipulatorStatusView;
import com.rgs.center.manipulator.RoboticManipulatorSnapshot;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Собирает REST/MVC-модель только из уже полученного snapshot Dobot. */
@Service
public class ManipulatorStatusQueryService {

    private final RoboticManipulatorStreamService manipulatorService;
    private final ManipulatorStatusEvaluator statusEvaluator;
    private final SettingsService settings;

    public ManipulatorStatusQueryService(
            RoboticManipulatorStreamService manipulatorService,
            ManipulatorStatusEvaluator statusEvaluator,
            SettingsService settings
    ) {
        this.manipulatorService = manipulatorService;
        this.statusEvaluator = statusEvaluator;
        this.settings = settings;
    }

    /** @return один согласованный read-only снимок без обращения к TCP */
    public ManipulatorStatusResponse snapshot() {
        boolean enabled = manipulatorService.isEnabled();
        RoboticManipulatorSnapshot snapshot = manipulatorService.snapshot();
        ManipulatorStatusView status = statusEvaluator.evaluate(
                enabled,
                snapshot
        );
        DobotFeedbackFrame feedback = snapshot.latestFeedback();
        int closedBit = settingInt(
                SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT
        );
        int openBit = settingInt(
                SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT
        );
        return new ManipulatorStatusResponse(
                Instant.now(),
                enabled,
                settings.getValue(SettingsService.MANIPULATOR_HOST, String.class),
                settingInt(SettingsService.MANIPULATOR_COMMAND_PORT),
                settingInt(SettingsService.MANIPULATOR_FEEDBACK_PORT),
                status.connectionStatus(),
                status.readiness(),
                status.reasons(),
                pose(feedback),
                motion(feedback),
                io(feedback),
                hatch(snapshot.hatchAnalogInput(), feedback, status, closedBit, openBit),
                safety(feedback),
                telemetry(snapshot, feedback),
                snapshot.lastError() == null ? "" : snapshot.lastError()
        );
    }

    private static ManipulatorStatusResponse.Pose pose(
            DobotFeedbackFrame feedback
    ) {
        if (feedback == null || feedback.pose() == null) {
            return new ManipulatorStatusResponse.Pose(
                    null, null, null, null, null, null
            );
        }
        var value = feedback.pose();
        return new ManipulatorStatusResponse.Pose(
                value.x(), value.y(), value.z(),
                value.rx(), value.ry(), value.rz()
        );
    }

    private static ManipulatorStatusResponse.Motion motion(
            DobotFeedbackFrame feedback
    ) {
        if (feedback == null) {
            return new ManipulatorStatusResponse.Motion(
                    null, null, null, null, null, null
            );
        }
        return new ManipulatorStatusResponse.Motion(
                feedback.robotMode(),
                feedback.enabled(),
                feedback.running(),
                feedback.queuedCommandRunning(),
                feedback.error(),
                feedback.currentCommandId()
        );
    }

    private static ManipulatorStatusResponse.Io io(
            DobotFeedbackFrame feedback
    ) {
        return feedback == null
                ? new ManipulatorStatusResponse.Io("-", "-")
                : new ManipulatorStatusResponse.Io(
                        hexadecimal(feedback.digitalInputs(), 16),
                        hexadecimal(feedback.digitalOutputs(), 16)
                );
    }

    private static ManipulatorStatusResponse.Hatch hatch(
            ManipulatorAnalogInputSnapshot analog,
            DobotFeedbackFrame feedback,
            ManipulatorStatusView status,
            int closedBit,
            int openBit
    ) {
        return new ManipulatorStatusResponse.Hatch(
                status.hatchStatus(),
                closedBit,
                feedback == null ? null : bit(feedback.digitalInputs(), closedBit),
                openBit,
                feedback == null ? null : bit(feedback.digitalInputs(), openBit),
                analog != null && analog.enabled(),
                analog == null ? 1 : analog.index(),
                analog == null ? null : analog.value(),
                analog == null ? null : instant(analog.receivedAtEpochMillis()),
                analog == null || analog.lastError() == null
                        ? "" : analog.lastError()
        );
    }

    private static ManipulatorStatusResponse.Safety safety(
            DobotFeedbackFrame feedback
    ) {
        if (feedback == null) {
            return new ManipulatorStatusResponse.Safety(
                    "-", null, null, null, null, null,
                    null, null, null, List.of()
            );
        }
        DobotSafetyState value = DobotSafetyState.decode(
                feedback.safetyState()
        );
        return new ManipulatorStatusResponse.Safety(
                hexadecimal(value.rawValue(), 2),
                value.emergencyStop(),
                value.protectiveStop(),
                value.reducedMode(),
                value.nonStop(),
                value.inMotion(),
                value.systemEmergencyStop(),
                value.userEmergencyStop(),
                value.outsideSafetyHome(),
                value.blockingFaults()
        );
    }

    private static ManipulatorStatusResponse.Telemetry telemetry(
            RoboticManipulatorSnapshot snapshot,
            DobotFeedbackFrame feedback
    ) {
        PeripheralWorkerSnapshot command = snapshot.commandChannel();
        PeripheralWorkerSnapshot stream = snapshot.feedbackChannel();
        long receivedAt = snapshot.latestFeedbackReceivedAtEpochMillis();
        return new ManipulatorStatusResponse.Telemetry(
                state(command),
                state(stream),
                feedback == null ? null : feedback.controllerTimestampMillis(),
                instant(receivedAt),
                receivedAt <= 0L
                        ? null : Math.max(0L, System.currentTimeMillis() - receivedAt),
                stream == null ? 0L : stream.bytesReceived(),
                stream == null ? 0L : stream.framesReceived(),
                stream == null ? 0L : stream.framesDropped()
        );
    }

    private int settingInt(String code) {
        return Math.toIntExact(settings.getValue(code, Long.class));
    }

    private static String state(PeripheralWorkerSnapshot snapshot) {
        return snapshot == null ? "STOPPED" : snapshot.state().name();
    }

    private static boolean bit(long mask, int index) {
        return index >= 0 && index < Long.SIZE
                && (mask & (1L << index)) != 0L;
    }

    private static Instant instant(long epochMillis) {
        return epochMillis <= 0L ? null : Instant.ofEpochMilli(epochMillis);
    }

    private static String hexadecimal(long value, int digits) {
        return String.format(Locale.ROOT, "0x%0" + digits + "X", value);
    }
}
