package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.Objects;

/** Последний полный набор показаний предусилителя SHP. */
public record ShpEquipmentSnapshot(
        DeviceHealth health,
        Double ambientTemperatureCelsius,
        Double humidityPercent,
        Double atmosphericPressureHpa,
        Double leakageCurrent,
        Double chargeOutputVoltage,
        Instant measurementsReceivedAt
) {
    public ShpEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
    }
}
