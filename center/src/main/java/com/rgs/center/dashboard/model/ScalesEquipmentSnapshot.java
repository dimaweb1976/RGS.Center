package com.rgs.center.dashboard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Текущее состояние весов и последнее значение массы. */
public record ScalesEquipmentSnapshot(
        DeviceHealth health,
        BigDecimal mass,
        BigDecimal massGrams,
        String unit,
        MeasurementStability stability,
        Instant readingReceivedAt
) {
    public ScalesEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(stability, "stability");
        unit = unit == null ? "" : unit;
    }

    /** Нормализованная устойчивость показаний без зависимости от драйвера весов. */
    public enum MeasurementStability {
        STABLE,
        UNSTABLE,
        UNKNOWN
    }
}
