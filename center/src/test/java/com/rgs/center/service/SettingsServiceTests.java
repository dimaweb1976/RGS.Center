package com.rgs.center.service;

import com.rgs.center.objects.SettingRecord;
import com.rgs.center.objects.SettingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsServiceTests {

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService();
        service.initializeDefaults();
    }

    @Test
    void initializesAndReturnsTypedDefaults() {
        assertEquals(
                SettingType.STRING.getCode(),
                service.getType(SettingsService.CAMERA_IP)
        );
        assertEquals(
                "192.168.1.13",
                service.getValue(SettingsService.CAMERA_IP, String.class)
        );
    }

    @Test
    void rejectsValueWithWrongType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.setValue(SettingsService.PING_TIMEOUT_MILLIS, "3000")
        );
    }

    @Test
    void providesComPortSettingsForTargetPeripheralDevices() {
        assertEquals(
                "",
                service.getValue(SettingsService.SCALES_PORT, String.class)
        );
        assertEquals(
                "COM5",
                service.getValue(SettingsService.SHP_PORT, String.class)
        );
    }

    @Test
    void providesGammaProTcpDefaults() {
        assertEquals(
                "127.0.0.1",
                service.getValue(SettingsService.GAMMA_PRO_HOST, String.class)
        );
        assertEquals(
                37569L,
                service.getValue(SettingsService.GAMMA_PRO_PORT, Long.class)
        );
    }

    @Test
    void loadsAndDumpsDatabaseShapedRecords() {
        service.load(List.of(
                new SettingRecord(
                        "measurement.enabled",
                        SettingType.BOOLEAN.getCode(),
                        true,
                        null,
                        null,
                        null
                )
        ));

        assertEquals(true, service.getValue("measurement.enabled"));
        assertEquals(
                true,
                service.dump().stream()
                        .filter(row -> row.stringId().equals("measurement.enabled"))
                        .findFirst()
                        .orElseThrow()
                        .booleanValue()
        );
    }
}
