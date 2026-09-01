package com.rgs.center.api.devices;

import com.rgs.center.nicole.NicoleConnectionStatus;
import com.rgs.center.nicole.NicoleReadinessStatus;

/**
 * Independent communication and technical states of NICOLE.
 *
 * @param connectionStatus result of the protocol PING/PONG exchange
 * @param technicalStatus readiness calculated from the last valid PONG
 * @param warningText decoded warning flags
 * @param snapshot latest valid values, or {@code null} before the first PONG
 */
public record NicoleStatusResponse(
        NicoleConnectionStatus connectionStatus,
        NicoleReadinessStatus technicalStatus,
        String warningText,
        NicoleSnapshotResponse snapshot
) {
}
