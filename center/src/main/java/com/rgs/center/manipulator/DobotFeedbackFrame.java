package com.rgs.center.manipulator;

import java.util.List;

/** Immutable subset of the official 1440-byte Dobot feedback packet. */
public record DobotFeedbackFrame(
        long digitalInputs,
        long digitalOutputs,
        long robotMode,
        long controllerTimestampMillis,
        List<Double> actualJointPositions,
        CartesianPose pose,
        boolean queuedCommandRunning,
        boolean enabled,
        boolean running,
        boolean error,
        long currentCommandId,
        int safetyState
) {
    public DobotFeedbackFrame {
        actualJointPositions = List.copyOf(actualJointPositions);
    }
}
