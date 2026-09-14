package com.rgs.center.measurement.safety;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Watches published device snapshots while an automatic run is active.
 * It never communicates with a device from the scheduler thread.
 */
@Service
public class ScenarioSafetyMonitor {

    private static final Set<String> REQUIRED_DEVICES = Set.of(
            DeviceCode.CAMERA.getCode(),
            DeviceCode.LENOVO_PC.getCode(),
            DeviceCode.NICOLE.getCode(),
            DeviceCode.BOSON.getCode(),
            DeviceCode.ROBOTIC_MANIPULATOR.getCode(),
            DeviceCode.BARCODE_SCANNER.getCode(),
            DeviceCode.SCALES.getCode(),
            DeviceCode.SHP.getCode(),
            DeviceCode.GAMMA_PRO.getCode()
    );

    private final DashboardSnapshotService dashboardService;
    private final ProductionMeasurementOrchestrator orchestrator;
    private final ScenarioIncidentService incidentService;

    public ScenarioSafetyMonitor(
            DashboardSnapshotService dashboardService,
            ProductionMeasurementOrchestrator orchestrator,
            ScenarioIncidentService incidentService
    ) {
        this.dashboardService = dashboardService;
        this.orchestrator = orchestrator;
        this.incidentService = incidentService;
    }

    @Scheduled(
            fixedDelayString = "${rgs.measurement.safety.monitor-interval-millis:1000}",
            initialDelayString = "${rgs.measurement.safety.monitor-initial-delay-millis:3000}"
    )
    public void inspect() {
        if (!orchestrator.isRunActive()) {
            return;
        }
        runtimeIssues().stream().findFirst().ifPresent(issue -> {
            var workflow = orchestrator.snapshot();
            incidentService.raise(
                    ScenarioIncidentSeverity.EMERGENCY,
                    issue.deviceCode(),
                    issue.code(),
                    issue.message(),
                    workflow.runId(),
                    workflow.activeObjectNumber(),
                    workflow.activeStep()
            );
            orchestrator.emergencyStop(issue.message());
        });
    }

    /** Acknowledges the alarm without claiming that equipment has recovered. */
    public ScenarioIncidentSnapshot acknowledge(String actor) {
        return incidentService.acknowledge(actor);
    }

    /** Resets the interlock only after the run stopped and every dependency is ready. */
    public ScenarioIncidentSnapshot resetAfterRecovery(String actor) {
        if (orchestrator.isRunActive()) {
            throw new IllegalStateException("Stop the automatic run before recovery reset");
        }
        List<SafetyIssue> issues = recoveryIssues();
        if (!issues.isEmpty()) {
            throw new IllegalStateException(
                    "Equipment is not ready: " + issues.getFirst().message()
            );
        }
        return incidentService.reset(actor);
    }

    List<SafetyIssue> runtimeIssues() {
        return requiredHealth().stream()
                .map(ScenarioSafetyMonitor::runtimeIssue)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    List<SafetyIssue> recoveryIssues() {
        return requiredHealth().stream()
                .map(ScenarioSafetyMonitor::recoveryIssue)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<DeviceHealth> requiredHealth() {
        return dashboardService.snapshot().equipment().healthSummary().stream()
                .filter(health -> REQUIRED_DEVICES.contains(health.code()))
                .toList();
    }

    private static SafetyIssue runtimeIssue(DeviceHealth health) {
        if (!health.enabled()) {
            return issue(health, "DEVICE_DISABLED", "is disabled during the run");
        }
        if (health.connection() != ConnectionState.ONLINE) {
            return issue(health, "CONNECTION_LOST",
                    "connection is " + health.connection());
        }
        if (health.stale()) {
            return issue(health, "STATUS_STALE", "status data is stale");
        }
        if (health.technical() == TechnicalState.ALERT
                || health.technical() == TechnicalState.NO_DATA) {
            String details = health.reasons().isEmpty()
                    ? "technical status is " + health.technical()
                    : String.join("; ", health.reasons());
            return issue(health, "TECHNICAL_" + health.technical(), details);
        }
        if (health.operation() == OperationalState.FAILED
                || health.operation() == OperationalState.UNKNOWN) {
            return issue(health, "OPERATION_" + health.operation(),
                    "operation status is " + health.operation());
        }
        return null;
    }

    private static SafetyIssue recoveryIssue(DeviceHealth health) {
        SafetyIssue runtime = runtimeIssue(health);
        if (runtime != null) {
            return runtime;
        }
        if (health.technical() != TechnicalState.READY) {
            return issue(health, "NOT_READY",
                    "technical status is " + health.technical());
        }
        if (health.operation() != OperationalState.IDLE
                && health.operation() != OperationalState.COMPLETED) {
            return issue(health, "NOT_IDLE",
                    "operation status is " + health.operation());
        }
        return null;
    }

    private static SafetyIssue issue(
            DeviceHealth health,
            String suffix,
            String details
    ) {
        return new SafetyIssue(
                health.code(),
                health.code().toUpperCase().replace('-', '_') + "_" + suffix,
                health.name() + ": " + details
        );
    }

    record SafetyIssue(String deviceCode, String code, String message) {
    }
}
