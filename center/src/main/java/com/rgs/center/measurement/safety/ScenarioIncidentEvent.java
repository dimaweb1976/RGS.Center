package com.rgs.center.measurement.safety;

import java.time.Instant;
import java.util.UUID;

/** One bounded audit event for an incident transition. */
public record ScenarioIncidentEvent(
        Instant occurredAt,
        UUID incidentId,
        String action,
        String actor,
        String details
) {
}
