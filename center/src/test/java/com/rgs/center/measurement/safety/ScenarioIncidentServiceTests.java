package com.rgs.center.measurement.safety;

import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScenarioIncidentServiceTests {

    @Test
    void requiresAcknowledgementBeforeRecoveryResetAndKeepsAudit() {
        ScenarioStatusService scenario = new ScenarioStatusService();
        ScenarioIncidentService service = new ScenarioIncidentService(
                scenario,
                Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC)
        );

        ScenarioIncidentSnapshot raised = service.raise(
                ScenarioIncidentSeverity.EMERGENCY,
                "nicole",
                "NICOLE_TECHNICAL_ALERT",
                "Nicole: cooler warning bit is set",
                UUID.fromString("70e49a95-0799-4c60-bf74-91f888a777f8"),
                3,
                MeasurementWorkflowStep.ACQUIRE_SPECTRUM
        );

        assertThat(raised.status()).isEqualTo(ScenarioIncidentStatus.ACTIVE);
        assertThat(service.blocksWorkflowStart()).isTrue();
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.EMERGENCY);
        assertThatThrownBy(() -> service.reset("operator"))
                .hasMessageContaining("acknowledged");

        ScenarioIncidentSnapshot acknowledged = service.acknowledge("operator");
        assertThat(acknowledged.status())
                .isEqualTo(ScenarioIncidentStatus.ACKNOWLEDGED);
        assertThat(acknowledged.acknowledgedBy()).isEqualTo("operator");
        assertThat(scenario.currentStatus())
                .isEqualTo(ScenarioStatus.RECOVERY_REQUIRED);

        ScenarioIncidentSnapshot cleared = service.reset("engineer");
        assertThat(cleared.status()).isEqualTo(ScenarioIncidentStatus.CLEARED);
        assertThat(cleared.clearedBy()).isEqualTo("engineer");
        assertThat(service.blocksWorkflowStart()).isFalse();
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.NORMAL);
        assertThat(service.events()).extracting(ScenarioIncidentEvent::action)
                .containsExactly("RAISED", "ACKNOWLEDGED", "CLEARED");
    }

    @Test
    void coalescesRepeatedReportsWithTheSameCode() {
        ScenarioIncidentService service = new ScenarioIncidentService(
                new ScenarioStatusService()
        );
        ScenarioIncidentSnapshot first = service.raise(
                ScenarioIncidentSeverity.EMERGENCY,
                "shp", "SHP_TECHNICAL_ALERT", "Leakage current exceeded",
                null, null, null
        );
        ScenarioIncidentSnapshot repeated = service.raise(
                ScenarioIncidentSeverity.EMERGENCY,
                "shp", "SHP_TECHNICAL_ALERT", "Leakage current exceeded",
                null, null, null
        );

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(service.events()).hasSize(1);
    }

    @Test
    void startsInMemoryWhenDeploymentTableHasNotBeenAppliedYet() {
        ScenarioIncidentRepository repository = mock(ScenarioIncidentRepository.class);
        when(repository.findLatestUnresolved()).thenThrow(
                new IllegalStateException("relation scenario_incidents does not exist")
        );
        ScenarioStatusService scenario = new ScenarioStatusService();

        ScenarioIncidentService service = new ScenarioIncidentService(
                scenario, repository, Clock.systemUTC()
        );
        service.raise(
                ScenarioIncidentSeverity.ALERT,
                "database", "PREFLIGHT_FAILED", "Database migration is pending",
                null, null, null
        );

        assertThat(service.persistenceAvailable()).isFalse();
        assertThat(service.snapshot().status())
                .isEqualTo(ScenarioIncidentStatus.ACTIVE);
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.EMERGENCY);
    }
}
