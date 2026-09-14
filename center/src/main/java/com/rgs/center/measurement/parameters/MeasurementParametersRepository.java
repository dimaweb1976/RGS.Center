package com.rgs.center.measurement.parameters;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Хранит единственный активный набор параметров измерения. */
@Repository
public class MeasurementParametersRepository {

    private final JdbcClient jdbcClient;

    public MeasurementParametersRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** @return активные параметры, созданные начальным SQL bootstrap */
    public MeasurementParameters findCurrent() {
        return jdbcClient.sql("""
                        SELECT measuring_time_minutes, volume_ml, geometry,
                               tare_mass_grams, version, updated_at
                        FROM measurement_parameters
                        WHERE id = 1
                        """)
                .query(MeasurementParametersRepository::map)
                .single();
    }

    /**
     * Обновляет строку только при совпадении версии.
     *
     * @return число изменённых строк: {@code 0} означает конфликт версии
     */
    public int update(MeasurementParameters value, long expectedVersion) {
        return jdbcClient.sql("""
                        UPDATE measurement_parameters
                        SET measuring_time_minutes = :measuringTimeMinutes,
                            volume_ml = :volumeMl,
                            geometry = :geometry,
                            tare_mass_grams = :tareMassGrams,
                            version = version + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = 1 AND version = :expectedVersion
                        """)
                .param("measuringTimeMinutes", value.measuringTimeMinutes())
                .param("volumeMl", value.volumeMl())
                .param("geometry", value.geometry().name())
                .param("tareMassGrams", value.tareMassGrams())
                .param("expectedVersion", expectedVersion)
                .update();
    }

    private static MeasurementParameters map(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new MeasurementParameters(
                resultSet.getLong("measuring_time_minutes"),
                resultSet.getDouble("volume_ml"),
                SampleGeometry.valueOf(resultSet.getString("geometry")),
                resultSet.getDouble("tare_mass_grams"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
