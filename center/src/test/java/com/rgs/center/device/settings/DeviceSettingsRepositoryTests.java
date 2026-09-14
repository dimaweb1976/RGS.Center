package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceSettingsRepositoryTests {

    private JdbcClient jdbcClient;
    private DeviceSettingsRepository repository;
    private long administratorId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:device-settings-" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"))
                .execute(dataSource);
        jdbcClient = JdbcClient.create(dataSource);
        repository = new DeviceSettingsRepository(jdbcClient);
        administratorId = jdbcClient.sql(
                        "SELECT id FROM users WHERE login = 'admin'"
                ).query(Long.class).single();
        jdbcClient.sql("""
                        INSERT INTO device_settings
                            (string_id, device_code, type, string_value)
                        VALUES
                            ('device.scales.port', 'scales', 4, 'COM5')
                        """).update();
    }

    @Test
    void updatesValueAndAuditWithOptimisticLock() {
        assertEquals(1, repository.update(
                "scales",
                "device.scales.port",
                SettingType.STRING,
                "COM12",
                0,
                administratorId
        ));

        DeviceSetting saved = repository.findByDevice("scales").getFirst();
        assertEquals("COM12", saved.value());
        assertEquals(1, saved.version());
        assertEquals(administratorId, saved.updatedBy());
        assertEquals("admin", saved.updatedByLogin());
        repository.insertAudit(
                "scales",
                "device.scales.port",
                SettingType.STRING,
                "COM5",
                "COM12",
                administratorId
        );
        DeviceSettingAudit audit = repository.findRecentAudit("scales", 10).getFirst();
        assertEquals("COM5", audit.oldValue());
        assertEquals("COM12", audit.newValue());
        assertEquals("admin", audit.changedByLogin());
        assertEquals(0, repository.update(
                "scales",
                "device.scales.port",
                SettingType.STRING,
                "COM13",
                0,
                administratorId
        ));
    }

    @Test
    void insertsMissingSettingOnlyOnce() {
        assertEquals(1, repository.insert(
                "camera",
                "device.camera.ip",
                SettingType.STRING,
                "192.168.1.13"
        ));
        assertEquals(0, repository.insert(
                "camera",
                "device.camera.ip",
                SettingType.STRING,
                "192.168.1.14"
        ));
    }
}
