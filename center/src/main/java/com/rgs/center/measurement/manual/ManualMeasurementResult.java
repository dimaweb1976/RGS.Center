package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable terminal result persisted for measurement history. */
public record ManualMeasurementResult(
        UUID id,
        String sampleCode,
        BigDecimal grossMassGrams,
        BigDecimal netMassGrams,
        MeasurementParameters parameters,
        Instant startedAt,
        Instant finishedAt,
        ManualMeasurementState status,
        String spectrumReference,
        String error
) {
}
