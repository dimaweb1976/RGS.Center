package com.rgs.center.manipulator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Отформатированная read-only модель таблицы состояния манипулятора. */
public record RoboticManipulatorViewModel(
        ManipulatorConnectionStatus connectionStatus,
        ManipulatorReadinessStatus readiness,
        String robotMode,
        String position,
        String orientation,
        String motion,
        String commandId,
        String digitalInputs,
        String digitalOutputs,
        String analogInput,
        String hatch,
        String safety,
        String lastFeedback,
        String details
) {
    private static final String EMPTY = "-";
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static RoboticManipulatorViewModel from(
            RoboticManipulatorSnapshot snapshot,
            ManipulatorStatusView status
    ) {
        DobotFeedbackFrame feedback = snapshot == null
                ? null : snapshot.latestFeedback();
        if (feedback == null) {
            return new RoboticManipulatorViewModel(
                    status.connectionStatus(), status.readiness(), EMPTY,
                    EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
                    analogInput(snapshot), status.hatchStatus().getLabel(),
                    EMPTY, EMPTY,
                    String.join("; ", status.reasons())
            );
        }
        CartesianPose pose = feedback.pose();
        return new RoboticManipulatorViewModel(
                status.connectionStatus(),
                status.readiness(),
                robotMode(feedback.robotMode()),
                position(pose),
                orientation(pose),
                motion(feedback),
                Long.toString(feedback.currentCommandId()),
                hexadecimal(feedback.digitalInputs()),
                hexadecimal(feedback.digitalOutputs()),
                analogInput(snapshot),
                status.hatchStatus().getLabel(),
                safety(feedback.safetyState()),
                dateTime(snapshot.latestFeedbackReceivedAtEpochMillis()),
                status.reasons().isEmpty()
                        ? "-" : String.join("; ", status.reasons())
        );
    }

    private static String position(CartesianPose pose) {
        if (pose == null) return EMPTY;
        return String.format(
                Locale.ROOT,
                "X: %.3f; Y: %.3f; Z: %.3f",
                pose.x(), pose.y(), pose.z()
        );
    }

    private static String orientation(CartesianPose pose) {
        if (pose == null) return EMPTY;
        return String.format(
                Locale.ROOT,
                "Rx: %.3f; Ry: %.3f; Rz: %.3f",
                pose.rx(), pose.ry(), pose.rz()
        );
    }

    private static String motion(DobotFeedbackFrame feedback) {
        if (feedback.running()) return "Running";
        if (feedback.queuedCommandRunning()) return "Queued";
        return "Stopped";
    }

    private static String robotMode(long mode) {
        return mode == 5L ? "5 (Enabled / idle)" : Long.toString(mode);
    }

    private static String hexadecimal(long value) {
        return String.format(Locale.ROOT, "0x%016X", value);
    }

    private static String analogInput(RoboticManipulatorSnapshot snapshot) {
        if (snapshot == null || snapshot.hatchAnalogInput() == null) {
            return EMPTY;
        }
        ManipulatorAnalogInputSnapshot analog = snapshot.hatchAnalogInput();
        if (!analog.enabled()) return "Disabled";
        if (analog.value() == null) return "AI(" + analog.index() + "): no data";
        return String.format(
                Locale.ROOT,
                "AI(%d): %.3f",
                analog.index(),
                analog.value()
        );
    }

    private static String safety(int rawValue) {
        DobotSafetyState state = DobotSafetyState.decode(rawValue);
        String raw = String.format(Locale.ROOT, "0x%02X", state.rawValue());
        java.util.List<String> faults = state.blockingFaults();
        return faults.isEmpty() ? raw + " (no blocking fault)"
                : raw + " (" + String.join(", ", faults) + ")";
    }

    private static String dateTime(long epochMillis) {
        if (epochMillis <= 0L) return EMPTY;
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME);
    }
}
