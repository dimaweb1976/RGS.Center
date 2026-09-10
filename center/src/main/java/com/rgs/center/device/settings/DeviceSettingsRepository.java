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
}
