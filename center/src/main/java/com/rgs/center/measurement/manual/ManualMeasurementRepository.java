package com.rgs.center.measurement.manual;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persists terminal manual-measurement results in one transaction. */
@Repository
public class ManualMeasurementRepository {

    private final JdbcClient jdbcClient;

    public ManualMeasurementRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(ManualMeasurementResult value) {
        int updated = jdbcClient.sql("""
                        INSERT INTO manual_measurements
                            (id, sample_code, gross_mass_grams, net_mass_grams,
                             measuring_time_minutes, volume_ml, geometry,
                             tare_mass_grams, started_at, finished_at, status,
                             spectrum_reference, error_message)
                        VALUES
                            (:id, :sampleCode, :grossMass, :netMass,
                             :measuringTime, :volume, :geometry,
                             :tareMass, :startedAt, :finishedAt, :status,
                             :spectrumReference, :error)
                        """)
                .param("id", value.id())
                .param("sampleCode", value.sampleCode())
                .param("grossMass", value.grossMassGrams())
                .param("netMass", value.netMassGrams())
                .param("measuringTime", value.parameters().measuringTimeMinutes())
                .param("volume", value.parameters().volumeMl())
                .param("geometry", value.parameters().geometry().name())
                .param("tareMass", value.parameters().tareMassGrams())
                .param("startedAt", value.startedAt())
                .param("finishedAt", value.finishedAt())
                .param("status", value.status().name())
                .param("spectrumReference", value.spectrumReference())
                .param("error", value.error())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Manual measurement result was not saved");
        }
    }
}
