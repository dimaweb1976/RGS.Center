package com.rgs.center.measurement.workflow;

import java.time.Instant;
import java.util.Objects;

/** One bounded audit entry produced by the workflow state machine. */
public record MeasurementWorkflowEvent(
        Instant occurredAt,
        Integer objectNumber,
        MeasurementWorkflowStep step,
        int attempt,
        String message
) {
    public MeasurementWorkflowEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(message, "message");
    }
}
