package com.rgs.center.measurement.safety;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioSafetyMonitorTests {

    @Test
    void stopsActiveRunWhenPublishedTechnicalStatusBecomesAlert() {
        DashboardSnapshotService dashboard = mock(DashboardSnapshotService.class);
        DashboardSnapshot snapshot = mock(DashboardSnapshot.class);
        EquipmentSnapshot equipment = mock(EquipmentSnapshot.class);
        ProductionMeasurementOrchestrator orchestrator = mock(
                ProductionMeasurementOrchestrator.class
        );
        ScenarioStatusService scenario = new ScenarioStatusService();
        ScenarioIncidentService incidents = new ScenarioIncidentService(scenario);
        MeasurementWorkflowSnapshot workflow = mock(MeasurementWorkflowSnapshot.class);

        DeviceHealth nicole = new DeviceHealth(
                "nicole", "Nicole", "192.168.1.15:5000", true,
                ConnectionState.ONLINE, TechnicalState.ALERT,
                OperationalState.IDLE, Instant.parse("2026-09-14T12:00:00Z"),
                false, List.of("Cooler warning bit 2"), ""
        );
        when(dashboard.snapshot()).thenReturn(snapshot);
        when(snapshot.equipment()).thenReturn(equipment);
        when(equipment.healthSummary()).thenReturn(List.of(nicole));
        when(orchestrator.isRunActive()).thenReturn(true);
        when(orchestrator.snapshot()).thenReturn(workflow);
        when(workflow.runId()).thenReturn(
                UUID.fromString("d99d0d2f-5991-4ff5-aaf8-c0af595f29db")
        );
        when(workflow.activeObjectNumber()).thenReturn(2);
        when(workflow.activeStep()).thenReturn(MeasurementWorkflowStep.READ_WEIGHT);

        new ScenarioSafetyMonitor(dashboard, orchestrator, incidents).inspect();

        assertThat(incidents.snapshot().severity())
                .isEqualTo(ScenarioIncidentSeverity.EMERGENCY);
        assertThat(incidents.snapshot().code())
                .isEqualTo("NICOLE_TECHNICAL_ALERT");
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.EMERGENCY);
        verify(orchestrator).emergencyStop("Nicole: Cooler warning bit 2");
    }

    @Test
    void doesNotInspectEquipmentWhileWorkflowIsIdle() {
        DashboardSnapshotService dashboard = mock(DashboardSnapshotService.class);
        ProductionMeasurementOrchestrator orchestrator = mock(
                ProductionMeasurementOrchestrator.class
        );
        when(orchestrator.isRunActive()).thenReturn(false);

        ScenarioIncidentService incidents = new ScenarioIncidentService(
                new ScenarioStatusService()
        );
        new ScenarioSafetyMonitor(dashboard, orchestrator, incidents).inspect();

        assertThat(incidents.snapshot().present()).isFalse();
    }
}
