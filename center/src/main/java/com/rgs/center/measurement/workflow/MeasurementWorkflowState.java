package com.rgs.center.measurement.workflow;

/** High-level lifecycle of one production measurement run. */
public enum MeasurementWorkflowState {
    IDLE,
    PRECHECK,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED
}
