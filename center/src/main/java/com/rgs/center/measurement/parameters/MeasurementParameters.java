package com.rgs.center.measurement.parameters;

import java.time.Instant;
import java.util.Objects;

/** Неизменяемый набор параметров, применяемый к следующему измерению. */
public record MeasurementParameters(
        long measuringTimeMinutes,
        double volumeMl,
        SampleGeometry geometry,
        double tareMassGrams,
        long version,
        Instant updatedAt
) {
    public MeasurementParameters {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
