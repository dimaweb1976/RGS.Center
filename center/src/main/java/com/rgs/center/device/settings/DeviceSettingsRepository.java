package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

/** PostgreSQL persistence for typed device connection settings. */
@Repository
public class DeviceSettingsRepository {

    private static final String SELECT = """
            SELECT s.string_id,
                   s.device_code,
                   s.type,
                   s.boolean_value,
                   s.long_value,
                   s.double_value,
                   s.string_value,
                   s.version,
                   s.updated_at,
                   s.updated_by,
                   u.login AS updated_by_login
            FROM device_settings s
            LEFT JOIN users u ON u.id = s.updated_by
            """;

    private final JdbcClient jdbcClient;

    public DeviceSettingsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DeviceSetting> findAll() {
        return jdbcClient.sql(SELECT + " ORDER BY s.device_code, s.string_id")
                .query(DeviceSettingsRepository::map)
                .list();
    }

    public List<DeviceSetting> findByDevice(String deviceCode) {
        return jdbcClient.sql(SELECT + " WHERE s.device_code = :deviceCode"
                        + " ORDER BY s.string_id")
                .param("deviceCode", deviceCode)
                .query(DeviceSettingsRepository::map)
                .list();
    }

    public int insert(
            String deviceCode,
            String settingId,
            SettingType type,
            Object value
    ) {
        return jdbcClient.sql("""
                        INSERT INTO device_settings
                            (string_id, device_code, type, boolean_value,
                             long_value, double_value, string_value)
                        SELECT :settingId, :deviceCode, :type, :booleanValue,
                               :longValue, :doubleValue, :stringValue
                        WHERE NOT EXISTS (
                            SELECT 1 FROM device_settings WHERE string_id = :settingId
                        )
                        """)
                .param("settingId", settingId)
                .param("deviceCode", deviceCode)
                .param("type", type.getCode())
                .param("booleanValue", type == SettingType.BOOLEAN ? value : null, Types.BOOLEAN)
                .param("longValue", type == SettingType.LONG ? value : null, Types.BIGINT)
                .param("doubleValue", type == SettingType.DOUBLE ? value : null, Types.DOUBLE)
                .param("stringValue", type == SettingType.STRING ? value : null, Types.VARCHAR)
                .update();
    }

    public int update(
            String deviceCode,
            String settingId,
            SettingType type,
            Object value,
            long expectedVersion,
            long updatedBy
    ) {
        return jdbcClient.sql("""
                        UPDATE device_settings
                        SET boolean_value = :booleanValue,
                            long_value = :longValue,
                            double_value = :doubleValue,
                            string_value = :stringValue,
                            version = version + 1,
                            updated_at = CURRENT_TIMESTAMP,
                            updated_by = :updatedBy
                        WHERE string_id = :settingId
                          AND device_code = :deviceCode
                          AND type = :type
                          AND version = :expectedVersion
                        """)
                .param("booleanValue", type == SettingType.BOOLEAN ? value : null, Types.BOOLEAN)
                .param("longValue", type == SettingType.LONG ? value : null, Types.BIGINT)
                .param("doubleValue", type == SettingType.DOUBLE ? value : null, Types.DOUBLE)
                .param("stringValue", type == SettingType.STRING ? value : null, Types.VARCHAR)
                .param("updatedBy", updatedBy)
                .param("settingId", settingId)
                .param("deviceCode", deviceCode)
                .param("type", type.getCode())
                .param("expectedVersion", expectedVersion)
                .update();
    }

    public void insertAudit(
            String deviceCode,
            String settingId,
            SettingType type,
            Object oldValue,
            Object newValue,
            long changedBy
    ) {
        jdbcClient.sql("""
                        INSERT INTO device_settings_audit
                            (string_id, device_code, type, old_value,
                             new_value, changed_by)
                        VALUES
                            (:settingId, :deviceCode, :type, :oldValue,
                             :newValue, :changedBy)
                        """)
                .param("settingId", settingId)
                .param("deviceCode", deviceCode)
                .param("type", type.getCode())
                .param("oldValue", displayValue(oldValue), Types.VARCHAR)
                .param("newValue", displayValue(newValue), Types.VARCHAR)
                .param("changedBy", changedBy)
                .update();
    }

    public List<DeviceSettingAudit> findRecentAudit(String deviceCode, int limit) {
        return jdbcClient.sql("""
                        SELECT a.id,
                               a.string_id,
                               a.device_code,
                               a.type,
                               a.old_value,
                               a.new_value,
                               a.changed_at,
                               a.changed_by,
                               u.login AS changed_by_login
                        FROM device_settings_audit a
                        LEFT JOIN users u ON u.id = a.changed_by
                        WHERE a.device_code = :deviceCode
                        ORDER BY a.id DESC
                        LIMIT :limit
                        """)
                .param("deviceCode", deviceCode)
                .param("limit", limit)
                .query(DeviceSettingsRepository::mapAudit)
                .list();
    }

    private static DeviceSetting map(ResultSet resultSet, int rowNumber)
            throws SQLException {
        SettingType type = SettingType.fromCode(resultSet.getInt("type"));
        Object value = switch (type) {
            case BOOLEAN -> resultSet.getObject("boolean_value", Boolean.class);
            case LONG -> resultSet.getObject("long_value", Long.class);
            case DOUBLE -> resultSet.getObject("double_value", Double.class);
            case STRING -> resultSet.getString("string_value");
        };
        Number updatedBy = (Number) resultSet.getObject("updated_by");
        Instant updatedAt = resultSet.getTimestamp("updated_at").toInstant();
        return new DeviceSetting(
                resultSet.getString("string_id"),
                resultSet.getString("device_code"),
                type,
                value,
                resultSet.getLong("version"),
                updatedAt,
                updatedBy == null ? null : updatedBy.longValue(),
                resultSet.getString("updated_by_login")
        );
    }

    private static DeviceSettingAudit mapAudit(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new DeviceSettingAudit(
                resultSet.getLong("id"),
                resultSet.getString("string_id"),
                resultSet.getString("device_code"),
                SettingType.fromCode(resultSet.getInt("type")),
                resultSet.getString("old_value"),
                resultSet.getString("new_value"),
                resultSet.getTimestamp("changed_at").toInstant(),
                resultSet.getLong("changed_by"),
                resultSet.getString("changed_by_login")
        );
    }

    private static String displayValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
