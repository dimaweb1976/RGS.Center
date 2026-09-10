package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.Objects;

/** Состояние BOSON, доступного RGSCenter через Gamma-PRO. */
public record BosonEquipmentSnapshot(
        DeviceHealth health,
        Boolean highVoltageRequested,
        Integer requestedHighVoltage,
        String highVoltageValueType,
        Boolean commandAccepted,
        Instant commandAcceptedAt,
        String sourceStatus
) {
    public BosonEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        highVoltageValueType = normalize(highVoltageValueType);
        sourceStatus = normalize(sourceStatus);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
