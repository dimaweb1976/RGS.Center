package com.rgs.center.api.devices;

import java.time.Instant;
import java.util.List;

/** Immutable snapshot returned to a separate frontend by {@code /api/v1/devices}. */
public record DevicesResponse(
        Instant generatedAt,
        List<DeviceResponse> devices,
        NicoleStatusResponse nicole,
        ShpStatusResponse shp
) {
    /** Prevents callers from modifying the published device collection. */
    public DevicesResponse {
        devices = List.copyOf(devices);
    }
}
