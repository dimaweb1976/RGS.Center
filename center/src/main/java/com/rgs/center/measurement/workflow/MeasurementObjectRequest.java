package com.rgs.center.measurement.workflow;

/** Object discovered by the camera and queued for production processing. */
public record MeasurementObjectRequest(int number, double x, double y) {
    public MeasurementObjectRequest {
        if (number <= 0) {
            throw new IllegalArgumentException("Object number must be positive");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Object coordinates must be finite");
        }
    }
}
