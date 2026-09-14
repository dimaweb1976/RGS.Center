package com.rgs.center.measurement.workflow;

import com.rgs.center.measurement.parameters.MeasurementParameters;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consistent read-only view of one production run. */
public record MeasurementWorkflowSnapshot(
        UUID runId,
        MeasurementWorkflowState state,
        MeasurementWorkflowStep activeStep,
        Integer activeObjectNumber,
        Instant startedAt,
        Instant finishedAt,
        MeasurementParameters parameters,
        String lastError,
        List<MeasurementObjectSnapshot> objects,
        List<MeasurementWorkflowEvent> events
) {
    public MeasurementWorkflowSnapshot {
        objects = List.copyOf(objects);
        events = List.copyOf(events);
    }
}
