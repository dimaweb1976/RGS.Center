package com.rgs.center.scales;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/** Immutable current state exposed by the background scales service. */
public record ScalesSnapshot(
        String deviceCode,
        String portName,
        boolean connected,
        int baudRate,
        long lastRequestEpochSeconds,
        long lastResponseEpochSeconds,
        String lastError,
        ScalesReading reading,
        PeripheralWorkerSnapshot communication
) {
}
