package com.rgs.center.connection;

/** Result of applying saved connection settings to one managed device. */
public record DeviceReconfigurationResult(
        String deviceCode,
        boolean applied,
        String status,
        String message
) {
}
