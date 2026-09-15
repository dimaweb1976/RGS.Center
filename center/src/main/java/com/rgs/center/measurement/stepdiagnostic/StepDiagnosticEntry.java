package com.rgs.center.measurement.stepdiagnostic;

import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;

import java.time.Instant;
import java.util.Objects;

/** Immutable result of one object/step in the diagnostic session. */
public record StepDiagnosticEntry(
        int sequence,
        int objectNumber,
        MeasurementWorkflowStep step,
        StepDiagnosticStatus status,
        String explanation,
        String result,
        Instant decidedAt
) {
    public StepDiagnosticEntry {
        if (sequence < 0 || objectNumber <= 0 || step == MeasurementWorkflowStep.PRECHECK) {
            throw new IllegalArgumentException("Invalid diagnostic step identity");
        }
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(result, "result");
    }
}
