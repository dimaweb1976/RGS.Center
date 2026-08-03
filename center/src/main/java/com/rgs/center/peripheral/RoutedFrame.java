package com.rgs.center.peripheral;

import java.util.Arrays;

/** Неизменяемый входящий кадр с метаданными источника и времени. */
public record RoutedFrame(
        String sourceId,
        byte[] payload,
        long receivedAtEpochMillis
) {
    public RoutedFrame {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Source id must not be blank");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
