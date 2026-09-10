package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.Objects;

/** Состояние соединения и последней операции сканера штрих-кодов. */
public record BarcodeEquipmentSnapshot(
        DeviceHealth health,
        String portName,
        Integer baudRate,
        BarcodeOperationState scanState,
        int currentAttempt,
        int maxAttempts,
        Instant lastHeartbeatAt,
        Instant lastSuccessfulScanAt,
        String lastBarcode,
        String lastConnectionError,
        String lastScanError
) {
    public BarcodeEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        portName = normalize(portName);
        Objects.requireNonNull(scanState, "scanState");
        lastBarcode = normalize(lastBarcode);
        lastConnectionError = normalize(lastConnectionError);
        lastScanError = normalize(lastScanError);
        if (currentAttempt < 0 || maxAttempts < 0) {
            throw new IllegalArgumentException(
                    "Scan attempt counters must not be negative"
            );
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    /** Прикладное состояние управляемого чтения кода. */
    public enum BarcodeOperationState {
        IDLE,
        SCANNING,
        SUCCESS,
        TIMEOUT,
        CANCELLED,
        ERROR
    }
}
