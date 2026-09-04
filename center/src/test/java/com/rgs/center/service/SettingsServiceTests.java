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
                250L,
                service.getValue(
                        SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS,
                        Long.class
                )
        );
        assertEquals(
                5000L,
                service.getValue(
                        SettingsService
                                .BARCODE_SCANNER_SCAN_ATTEMPT_TIMEOUT_MILLIS,
                        Long.class
                )
        );
        assertEquals(
                "AUTO",
                service.getValue(
                        SettingsService.BARCODE_SCANNER_DATA_SUFFIX,
                        String.class
                )
        );
        assertEquals(
                "",
                service.getValue(SettingsService.SCALES_PORT, String.class)
        );
        assertEquals(
                9600L,
                service.getValue(SettingsService.SCALES_BAUD_RATE, Long.class)
        );
        assertEquals(
                700L,
                service.getValue(
                        SettingsService.SCALES_RESPONSE_TIMEOUT_MILLIS,
                        Long.class
                )
        );
        assertEquals(
                "COM5",
                service.getValue(SettingsService.SHP_PORT, String.class)
        );
        assertEquals(
                105.0,
                service.getValue(
                        SettingsService.SHP_READY_MAX_CHARGE_OUTPUT_VOLTAGE,
                        Double.class
                )
        );
        assertEquals(
                50.0,
                service.getValue(
                        SettingsService.SHP_READY_MAX_LEAKAGE_CURRENT,
                        Double.class
                )
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
        assertEquals(
                "BOSON",
                service.getValue(SettingsService.BOSON_MCA_NAME, String.class)
        );
        assertEquals(
                "Ch1",
                service.getValue(
                        SettingsService.BOSON_CHANNEL_NAME,
                        String.class
                )
        );
    }

    @Test
    void providesNicoleTcpDefaults() {
        assertEquals(
                5000L,
                service.getValue(SettingsService.NICOLE_PORT, Long.class)
        );
        assertEquals(
                3000L,
                service.getValue(
                        SettingsService.NICOLE_RESPONSE_TIMEOUT_MILLIS,
                        Long.class
                )
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
