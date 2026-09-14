package com.rgs.center.measurement.safety;

import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioIncidentRepositoryTests {

    @Test
    void restoresUnresolvedIncidentAndPersistsOperatorLifecycle() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:safety-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"))
                .execute(dataSource);
        ScenarioIncidentRepository repository = new ScenarioIncidentRepository(
                JdbcClient.create(dataSource)
        );
        ScenarioStatusService firstScenario = new ScenarioStatusService();
        ScenarioIncidentService first = new ScenarioIncidentService(
                firstScenario,
                repository,
                Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC)
        );

        ScenarioIncidentSnapshot raised = first.raise(
                ScenarioIncidentSeverity.EMERGENCY,
                "shp", "SHP_TECHNICAL_ALERT", "Leakage current exceeded",
                UUID.fromString("247305c5-77ef-4d8d-8ef8-d4877caa0afd"),
                4, MeasurementWorkflowStep.ACQUIRE_SPECTRUM
        );

        ScenarioStatusService restoredScenario = new ScenarioStatusService();
        ScenarioIncidentService restored = new ScenarioIncidentService(
                restoredScenario,
                repository,
                Clock.fixed(Instant.parse("2026-09-14T12:01:00Z"), ZoneOffset.UTC)
        );
        assertThat(restored.snapshot().id()).isEqualTo(raised.id());
        assertThat(restoredScenario.currentStatus()).isEqualTo(ScenarioStatus.EMERGENCY);

        restored.acknowledge("operator");
        assertThat(repository.findLatestUnresolved()).get()
                .extracting(ScenarioIncidentSnapshot::status)
                .isEqualTo(ScenarioIncidentStatus.ACKNOWLEDGED);
        restored.reset("engineer");
        assertThat(repository.findLatestUnresolved()).isEmpty();
    }
}
