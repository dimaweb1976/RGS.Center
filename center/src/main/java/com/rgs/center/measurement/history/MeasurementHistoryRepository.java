package com.rgs.center.measurement.history;

import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Reads the unified history and persists terminal automatic-run objects. */
@Repository
public class MeasurementHistoryRepository {

    private static final String UNIFIED_SELECT = """
            SELECT mode, run_id, object_number, sample_code, coordinate_x,
                   coordinate_y, gross_mass_grams, net_mass_grams,
                   measuring_time_minutes, volume_ml, geometry,
                   tare_mass_grams, started_at, finished_at, status,
                   spectrum_reference, error_message
            FROM (
                SELECT 'MANUAL' AS mode,
                       id AS run_id,
                       1 AS object_number,
                       sample_code,
                       CAST(NULL AS DOUBLE PRECISION) AS coordinate_x,
                       CAST(NULL AS DOUBLE PRECISION) AS coordinate_y,
                       gross_mass_grams,
                       net_mass_grams,
                       measuring_time_minutes,
                       volume_ml,
                       geometry,
                       tare_mass_grams,
                       started_at,
                       finished_at,
                       status,
                       spectrum_reference,
                       error_message
                FROM manual_measurements
                UNION ALL
                SELECT 'AUTOMATIC' AS mode,
                       run_id,
                       object_number,
                       sample_code,
                       coordinate_x,
                       coordinate_y,
                       gross_mass_grams,
                       net_mass_grams,
                       measuring_time_minutes,
                       volume_ml,
                       geometry,
                       tare_mass_grams,
                       started_at,
                       finished_at,
                       status,
                       spectrum_reference,
                       error_message
                FROM automatic_measurements
            ) history
            """;

    private final JdbcClient jdbcClient;

    public MeasurementHistoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<MeasurementHistoryEntry> find(MeasurementHistoryCriteria criteria) {
        StringBuilder sql = new StringBuilder(UNIFIED_SELECT).append(" WHERE 1 = 1");
        Map<String, Object> parameters = new HashMap<>();
        if (!criteria.sampleCode().isBlank()) {
            sql.append(" AND LOWER(sample_code) LIKE :sampleCode");
            parameters.put(
                    "sampleCode",
                    "%" + criteria.sampleCode().toLowerCase(Locale.ROOT) + "%"
            );
        }
        if (criteria.mode() != null) {
            sql.append(" AND mode = :mode");
            parameters.put("mode", criteria.mode().name());
        }
        if (!criteria.status().isBlank()) {
            sql.append(" AND status = :status");
            parameters.put("status", criteria.status());
        }
        if (criteria.startedFrom() != null) {
            sql.append(" AND started_at >= :startedFrom");
            parameters.put("startedFrom", criteria.startedFrom());
        }
        if (criteria.startedBefore() != null) {
            sql.append(" AND started_at < :startedBefore");
            parameters.put("startedBefore", criteria.startedBefore());
        }
        sql.append(" ORDER BY started_at DESC, run_id, object_number LIMIT :limit");
        parameters.put("limit", criteria.limit());
        return jdbcClient.sql(sql.toString())
                .params(parameters)
                .query(MeasurementHistoryRepository::map)
                .list();
    }

    public Optional<MeasurementHistoryEntry> findManual(UUID runId) {
        return findOne("mode = 'MANUAL' AND run_id = :runId", Map.of("runId", runId));
    }

    public Optional<MeasurementHistoryEntry> findAutomatic(UUID runId, int objectNumber) {
        return findOne(
                "mode = 'AUTOMATIC' AND run_id = :runId AND object_number = :objectNumber",
                Map.of("runId", runId, "objectNumber", objectNumber)
        );
    }

    /** Stores every object from one terminal automatic-run snapshot. */
    @Transactional
    public void insertAutomaticRun(MeasurementWorkflowSnapshot snapshot) {
        if (snapshot.runId() == null || snapshot.startedAt() == null
                || snapshot.finishedAt() == null || snapshot.parameters() == null) {
            throw new IllegalArgumentException("Only a terminal automatic run can be stored");
        }
        for (MeasurementObjectSnapshot object : snapshot.objects()) {
            insertAutomaticObject(snapshot, object);
        }
    }

    private Optional<MeasurementHistoryEntry> findOne(
            String predicate,
            Map<String, Object> parameters
    ) {
        return jdbcClient.sql(UNIFIED_SELECT + " WHERE " + predicate)
                .params(parameters)
                .query(MeasurementHistoryRepository::map)
                .optional();
    }

    private void insertAutomaticObject(
            MeasurementWorkflowSnapshot run,
            MeasurementObjectSnapshot object
    ) {
        MeasurementParameters parameters = run.parameters();
        int updated = jdbcClient.sql("""
                        INSERT INTO automatic_measurements
                            (run_id, object_number, sample_code, coordinate_x,
                             coordinate_y, gross_mass_grams, net_mass_grams,
                             measuring_time_minutes, volume_ml, geometry,
                             tare_mass_grams, started_at, finished_at, status,
                             spectrum_reference, error_message)
                        VALUES
                            (:runId, :objectNumber, :sampleCode, :x,
                             :y, :grossMass, :netMass,
                             :measuringTime, :volume, :geometry,
                             :tareMass, :startedAt, :finishedAt, :status,
                             :spectrumReference, :error)
                        """)
                .param("runId", run.runId())
                .param("objectNumber", object.number())
                .param("sampleCode", normalize(object.barcode()))
                .param("x", object.x())
                .param("y", object.y())
                .param("grossMass", object.grossMassGrams(), Types.NUMERIC)
                .param("netMass", object.netMassGrams(), Types.NUMERIC)
                .param("measuringTime", parameters.measuringTimeMinutes())
                .param("volume", parameters.volumeMl())
                .param("geometry", parameters.geometry().name())
                .param("tareMass", parameters.tareMassGrams())
                .param("startedAt", run.startedAt())
                .param("finishedAt", run.finishedAt())
                .param("status", terminalStatus(run, object))
                .param("spectrumReference", normalize(object.spectrumReference()))
                .param("error", object.error().isBlank() ? normalize(run.lastError()) : object.error())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Automatic measurement object was not saved");
        }
    }

    private static MeasurementHistoryEntry map(ResultSet row, int rowNumber)
            throws SQLException {
        return new MeasurementHistoryEntry(
                MeasurementMode.valueOf(row.getString("mode")),
                row.getObject("run_id", UUID.class),
                row.getInt("object_number"),
                row.getString("sample_code"),
                nullableDouble(row, "coordinate_x"),
                nullableDouble(row, "coordinate_y"),
                row.getBigDecimal("gross_mass_grams"),
                row.getBigDecimal("net_mass_grams"),
                row.getLong("measuring_time_minutes"),
                row.getDouble("volume_ml"),
                SampleGeometry.valueOf(row.getString("geometry")),
                row.getDouble("tare_mass_grams"),
                row.getTimestamp("started_at").toInstant(),
                row.getTimestamp("finished_at").toInstant(),
                row.getString("status"),
                row.getString("spectrum_reference"),
                row.getString("error_message")
        );
    }

    private static Double nullableDouble(ResultSet row, String column)
            throws SQLException {
        double value = row.getDouble(column);
        return row.wasNull() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static String terminalStatus(
            MeasurementWorkflowSnapshot run,
            MeasurementObjectSnapshot object
    ) {
        if (!"QUEUED".equals(object.status()) && !"RUNNING".equals(object.status())) {
            return object.status();
        }
        return switch (run.state()) {
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            default -> object.status();
        };
    }
}
