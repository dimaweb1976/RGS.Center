package com.rgs.center.api.devices;

import java.time.Instant;
import java.time.LocalDateTime;

/** Detailed values decoded from the latest valid NICOLE PONG frame. */
public record NicoleSnapshotResponse(
        int resetCause,
        boolean ethernetUp,
        boolean dhcpLeased,
        boolean cryoControllerCommunication,
        boolean cryocoolerStarted,
        boolean advancedMode,
        int currentNitrogenLevel,
        int currentNitrogenPressure,
        int lowerLevelVoltage,
        int upperLevelVoltage,
        long nitrogenLevelFrequency,
        LocalDateTime controllerTime,
        int coolerErrorCode,
        int warningFlags,
        Instant receivedAt
) {
}
