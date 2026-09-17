package com.rgs.center.manipulator;

import com.rgs.center.peripheral.IncomingFrameRouterSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/** Consistent diagnostic view of both Dobot channels and the last telemetry. */
public record RoboticManipulatorSnapshot(
        boolean started,
        PeripheralWorkerSnapshot commandChannel,
        PeripheralWorkerSnapshot feedbackChannel,
        IncomingFrameRouterSnapshot router,
        DobotFeedbackFrame latestFeedback,
        long latestFeedbackReceivedAtEpochMillis,
        ManipulatorAnalogInputSnapshot hatchAnalogInput,
        String lastError,
        boolean feedbackOnly
) {
    public RoboticManipulatorSnapshot(
            boolean started, PeripheralWorkerSnapshot commandChannel,
            PeripheralWorkerSnapshot feedbackChannel, IncomingFrameRouterSnapshot router,
            DobotFeedbackFrame latestFeedback, long latestFeedbackReceivedAtEpochMillis,
            ManipulatorAnalogInputSnapshot hatchAnalogInput, String lastError
    ) {
        this(started, commandChannel, feedbackChannel, router, latestFeedback,
                latestFeedbackReceivedAtEpochMillis, hatchAnalogInput, lastError, false);
    }
}
