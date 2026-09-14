package com.rgs.center.measurement.manual;

/** Lifecycle of one operator-controlled measurement. */
public enum ManualMeasurementState {
    IDLE,
    PREPARING,
    ACQUIRING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}
