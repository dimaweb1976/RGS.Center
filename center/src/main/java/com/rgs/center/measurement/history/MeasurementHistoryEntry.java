package com.rgs.center.measurement.history;

import com.rgs.center.measurement.parameters.SampleGeometry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Complete immutable database view of one measured object. */
public record MeasurementHistoryEntry(
        MeasurementMode mode,
        UUID runId,
        int objectNumber,
        String sampleCode,
        Double x,
        Double y,
        BigDecimal grossMassGrams,
        BigDecimal netMassGrams,
        long measuringTimeMinutes,
        double volumeMl,
        SampleGeometry geometry,
        double tareMassGrams,
        Instant startedAt,
        Instant finishedAt,
        String status,
        String spectrumReference,
        String error
) {
}
