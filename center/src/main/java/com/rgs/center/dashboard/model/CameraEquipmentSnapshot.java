package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.Objects;

/** Текущее состояние камеры и последнего распознавания. */
public record CameraEquipmentSnapshot(
        DeviceHealth health,
        boolean imageAvailable,
        long imageVersion,
        boolean recognized,
        int detectedBottleCount,
        Instant capturedAt,
        String message
) {
    public CameraEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(message, "message");
        if (imageVersion < 0L) {
            throw new IllegalArgumentException("imageVersion must not be negative");
        }
        if (detectedBottleCount < 0) {
            throw new IllegalArgumentException(
                    "detectedBottleCount must not be negative"
            );
        }
    }
}
