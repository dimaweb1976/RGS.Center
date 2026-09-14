package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManualMeasurementRepositoryTests {

    private JdbcClient jdbcClient;
    private ManualMeasurementRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:manual-measurement-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"))
                .execute(dataSource);
        jdbcClient = JdbcClient.create(dataSource);
        repository = new ManualMeasurementRepository(jdbcClient);
    }

    @Test
    void insertsCompleteResultWithParameterSnapshot() {
        UUID id = UUID.fromString("96a6e194-a80a-4477-819f-a9ddd6e990f7");
        Instant started = Instant.parse("2026-09-14T08:00:00Z");
        repository.insert(new ManualMeasurementResult(
                id,
                "SAMPLE-42",
                new BigDecimal("125.500"),
                new BigDecimal("100.500"),
                new MeasurementParameters(
                        60, 300, SampleGeometry.BOTTLE, 25, 7, started
                ),
                started,
                started.plusSeconds(3600),
                ManualMeasurementState.COMPLETED,
                "spectrum-42",
                ""
        ));

        var saved = jdbcClient.sql("""
                        SELECT sample_code, net_mass_grams, geometry, status,
                               spectrum_reference
                        FROM manual_measurements WHERE id = :id
                        """)
                .param("id", id)
                .query((row, number) -> java.util.Map.of(
                        "sample", row.getString("sample_code"),
                        "net", row.getBigDecimal("net_mass_grams"),
                        "geometry", row.getString("geometry"),
                        "status", row.getString("status"),
                        "spectrum", row.getString("spectrum_reference")
                )).single();
        assertThat(saved.get("sample")).isEqualTo("SAMPLE-42");
        assertThat((BigDecimal) saved.get("net")).isEqualByComparingTo("100.5");
        assertThat(saved.get("geometry")).isEqualTo("BOTTLE");
        assertThat(saved.get("status")).isEqualTo("COMPLETED");
        assertThat(saved.get("spectrum")).isEqualTo("spectrum-42");
    }
}
