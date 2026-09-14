package com.rgs.center.measurement.safety;

import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;

import java.time.Instant;
import java.util.UUID;

/** Immutable incident data exposed to the workflow UI. */
public record ScenarioIncidentSnapshot(
        UUID id,
        ScenarioIncidentSeverity severity,
        ScenarioIncidentStatus status,
        String source,
        String code,
        String message,
        UUID runId,
        Integer objectNumber,
        MeasurementWorkflowStep step,
        Instant raisedAt,
        String acknowledgedBy,
        Instant acknowledgedAt,
        String clearedBy,
        Instant clearedAt
) {
    public boolean present() {
        return id != null;
    }

    public boolean canAcknowledge() {
        return status == ScenarioIncidentStatus.ACTIVE;
    }

    public boolean canReset() {
        return status == ScenarioIncidentStatus.ACKNOWLEDGED;
    }

    public static ScenarioIncidentSnapshot none() {
        return new ScenarioIncidentSnapshot(
                null, null, null, "", "", "", null, null, null,
                null, "", null, "", null
        );
    }
}
