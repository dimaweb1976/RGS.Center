package com.rgs.center.measurement.stepdiagnostic;

/** A diagnostic step is never counted as a production workflow completion. */
public enum StepDiagnosticStatus {
    WAITING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    ERROR
}
