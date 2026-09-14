package com.rgs.center.measurement.workflow;

import java.util.List;

/** Result of a read-only check performed immediately before a run. */
public record WorkflowPreflightReport(List<String> issues) {
    public WorkflowPreflightReport {
        issues = List.copyOf(issues);
    }

    public static WorkflowPreflightReport ready() {
        return new WorkflowPreflightReport(List.of());
    }

    public boolean isReady() {
        return issues.isEmpty();
    }
}
