package com.rgs.center.barcodescanner;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/** Immutable state shown on the barcode scanner service page. */
public record BarcodeScannerSnapshot(
        String deviceCode,
        String portName,
        boolean enabled,
        boolean connected,
        boolean initialized,
        int baudRate,
        BarcodeScannerOperationState operationState,
        int currentAttempt,
        int maxAttempts,
        long lastHeartbeatEpochSeconds,
        long lastScanEpochSeconds,
        String lastBarcode,
        String lastConnectionError,
        String lastScanError,
        PeripheralWorkerSnapshot communication
) {
}
