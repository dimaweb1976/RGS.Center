package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Consistent read-only state of the current manual measurement. */
public record ManualMeasurementSnapshot(
        UUID measurementId,
        ManualMeasurementState state,
        String currentOperation,
        String sampleCode,
        BigDecimal grossMassGrams,
        BigDecimal netMassGrams,
        MeasurementParameters parameters,
        Instant startedAt,
        Instant estimatedFinishAt,
        Instant finishedAt,
        String spectrumReference,
        String lastError
) {
}
