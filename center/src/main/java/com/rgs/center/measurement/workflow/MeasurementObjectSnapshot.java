package com.rgs.center.measurement.workflow;

import java.math.BigDecimal;

/** Immutable current result for one queued object. */
public record MeasurementObjectSnapshot(
        int number,
        double x,
        double y,
        String status,
        MeasurementWorkflowStep lastCompletedStep,
        String barcode,
        BigDecimal grossMassGrams,
        BigDecimal netMassGrams,
        String spectrumReference,
        String error
) {
}
