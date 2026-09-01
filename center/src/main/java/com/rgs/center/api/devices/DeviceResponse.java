package com.rgs.center.api.devices;

import com.rgs.center.objects.DeviceStatus;

import java.time.Instant;

/**
 * Stable REST representation of one row in the RGSCenter device table.
 *
 * @param code machine-readable device identifier
 * @param name display name
 * @param address IP address, TCP endpoint or COM port
 * @param enabled whether background communication is enabled by configuration
 * @param connectionStatus latest communication status
 * @param lastCheck time of the latest request or response, or {@code null}
 */
public record DeviceResponse(
        String code,
        String name,
        String address,
        boolean enabled,
        DeviceStatus connectionStatus,
        Instant lastCheck
) {
}
