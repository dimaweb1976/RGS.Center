package com.rgs.center.measurement.safety;

import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL persistence for safety incidents and operator decisions. */
@Repository
public class ScenarioIncidentRepository {

    private final JdbcClient jdbcClient;

    public ScenarioIncidentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(ScenarioIncidentSnapshot value) {
        jdbcClient.sql("""
                INSERT INTO scenario_incidents
                    (id, severity, status, source, code, message, run_id,
                     object_number, workflow_step, raised_at)
                VALUES
                    (:id, :severity, :status, :source, :code, :message, :runId,
                     :objectNumber, :workflowStep, :raisedAt)
                """)
                .param("id", value.id())
                .param("severity", value.severity().name())
                .param("status", value.status().name())
                .param("source", value.source())
                .param("code", value.code())
                .param("message", value.message())
                .param("runId", value.runId(), Types.OTHER)
                .param("objectNumber", value.objectNumber(), Types.INTEGER)
                .param("workflowStep", value.step() == null
                        ? null : value.step().name(), Types.VARCHAR)
                .param("raisedAt", value.raisedAt())
                .update();
    }

    public void acknowledge(ScenarioIncidentSnapshot value) {
        int updated = jdbcClient.sql("""
                UPDATE scenario_incidents
                   SET status = :status,
                       acknowledged_by = :actor,
                       acknowledged_at = :occurredAt
                 WHERE id = :id AND status = 'ACTIVE'
                """)
                .param("status", value.status().name())
                .param("actor", value.acknowledgedBy())
                .param("occurredAt", value.acknowledgedAt())
                .param("id", value.id())
                .update();
        requireSingleUpdate(updated, value.id());
    }

    public void clear(ScenarioIncidentSnapshot value) {
        int updated = jdbcClient.sql("""
                UPDATE scenario_incidents
                   SET status = :status,
                       cleared_by = :actor,
                       cleared_at = :occurredAt
                 WHERE id = :id AND status = 'ACKNOWLEDGED'
                """)
                .param("status", value.status().name())
                .param("actor", value.clearedBy())
                .param("occurredAt", value.clearedAt())
                .param("id", value.id())
                .update();
        requireSingleUpdate(updated, value.id());
    }

    public Optional<ScenarioIncidentSnapshot> findLatestUnresolved() {
        return jdbcClient.sql("""
                SELECT id, severity, status, source, code, message, run_id,
                       object_number, workflow_step, raised_at,
                       acknowledged_by, acknowledged_at, cleared_by, cleared_at
                  FROM scenario_incidents
                 WHERE status <> 'CLEARED'
                 ORDER BY raised_at DESC
                 LIMIT 1
                """)
                .query(ScenarioIncidentRepository::map)
                .optional();
    }

    private static ScenarioIncidentSnapshot map(ResultSet result, int row)
            throws SQLException {
        String step = result.getString("workflow_step");
        return new ScenarioIncidentSnapshot(
                result.getObject("id", UUID.class),
                ScenarioIncidentSeverity.valueOf(result.getString("severity")),
                ScenarioIncidentStatus.valueOf(result.getString("status")),
                result.getString("source"),
                result.getString("code"),
                result.getString("message"),
                result.getObject("run_id", UUID.class),
                result.getObject("object_number", Integer.class),
                step == null ? null : MeasurementWorkflowStep.valueOf(step),
                instant(result, "raised_at"),
                text(result, "acknowledged_by"),
                instant(result, "acknowledged_at"),
                text(result, "cleared_by"),
                instant(result, "cleared_at")
        );
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        var timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String text(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        return value == null ? "" : value;
    }

    private static void requireSingleUpdate(int updated, UUID id) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Concurrent incident update rejected for " + id
            );
        }
    }
}
