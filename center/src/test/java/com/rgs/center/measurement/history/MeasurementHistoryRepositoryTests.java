package com.rgs.center.measurement.history;

import com.rgs.center.measurement.manual.ManualMeasurementRepository;
import com.rgs.center.measurement.manual.ManualMeasurementResult;
import com.rgs.center.measurement.manual.ManualMeasurementState;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowState;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementHistoryRepositoryTests {

    private MeasurementHistoryRepository repository;
    private ManualMeasurementRepository manualRepository;
    private MeasurementParameters parameters;
    private Instant started;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:measurement-history-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"))
                .execute(dataSource);
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        repository = new MeasurementHistoryRepository(jdbcClient);
        manualRepository = new ManualMeasurementRepository(jdbcClient);
        started = Instant.parse("2026-09-14T08:00:00Z");
        parameters = new MeasurementParameters(
                60, 300, SampleGeometry.BOTTLE, 25, 7, started
        );
    }

    @Test
    void searchesManualAndAutomaticMeasurementsAndLoadsDetails() {
        UUID manualId = UUID.fromString("96a6e194-a80a-4477-819f-a9ddd6e990f7");
        manualRepository.insert(new ManualMeasurementResult(
                manualId, "MANUAL-42", new BigDecimal("125.5"),
                new BigDecimal("100.5"), parameters, started,
                started.plusSeconds(3600), ManualMeasurementState.COMPLETED,
                "manual-spectrum", ""
        ));

        UUID automaticId = UUID.fromString("4cc5596c-d790-46de-9a61-9818627befa8");
        repository.insertAutomaticRun(new MeasurementWorkflowSnapshot(
                automaticId, MeasurementWorkflowState.COMPLETED, null, null,
                started.plusSeconds(7200), started.plusSeconds(10800), parameters,
                "", List.of(new MeasurementObjectSnapshot(
                        3, 120.5, 44.25, "COMPLETED", null, "AUTO-99",
                        new BigDecimal("130.0"), new BigDecimal("105.0"),
                        "auto-spectrum", ""
                )), List.of()
        ));

        List<MeasurementHistoryEntry> all = repository.find(criteria("", null, ""));
        assertThat(all).extracting(MeasurementHistoryEntry::mode)
                .containsExactly(MeasurementMode.AUTOMATIC, MeasurementMode.MANUAL);
        assertThat(repository.find(criteria("manual", MeasurementMode.MANUAL, "COMPLETED")))
                .singleElement()
                .extracting(MeasurementHistoryEntry::runId)
                .isEqualTo(manualId);
        assertThat(repository.findManual(manualId)).get()
                .extracting(MeasurementHistoryEntry::x)
                .isNull();
        assertThat(repository.findAutomatic(automaticId, 3)).get()
                .satisfies(entry -> {
                    assertThat(entry.sampleCode()).isEqualTo("AUTO-99");
                    assertThat(entry.x()).isEqualTo(120.5);
                    assertThat(entry.spectrumReference()).isEqualTo("auto-spectrum");
                });
    }

    @Test
    void convertsUnprocessedObjectsToTerminalRunStatus() {
        UUID runId = UUID.fromString("f9f9fb64-d4fc-4eb7-98fb-2585bad52bf6");
        repository.insertAutomaticRun(new MeasurementWorkflowSnapshot(
                runId, MeasurementWorkflowState.FAILED, null, null,
                started, started.plusSeconds(10), parameters, "robot timeout",
                List.of(new MeasurementObjectSnapshot(
                        1, 10, 20, "QUEUED", null, "", null, null, "", ""
                )), List.of()
        ));

        MeasurementHistoryEntry saved = repository.findAutomatic(runId, 1).orElseThrow();
        assertThat(saved.status()).isEqualTo("FAILED");
        assertThat(saved.error()).isEqualTo("robot timeout");
        assertThat(saved.grossMassGrams()).isNull();
    }

    private MeasurementHistoryCriteria criteria(
            String sample,
            MeasurementMode mode,
            String status
    ) {
        return new MeasurementHistoryCriteria(sample, mode, status, null, null, 25);
    }
}
