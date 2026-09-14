package com.rgs.center.measurement.history;

import java.time.Instant;

/** Validated search criteria for the measurement database. */
public record MeasurementHistoryCriteria(
        String sampleCode,
        MeasurementMode mode,
        String status,
        Instant startedFrom,
        Instant startedBefore,
        int limit
) {
    public MeasurementHistoryCriteria {
        sampleCode = normalize(sampleCode);
        status = normalize(status).toUpperCase();
        if (limit < 1 || limit > 5000) {
            throw new IllegalArgumentException("History limit must be between 1 and 5000");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
