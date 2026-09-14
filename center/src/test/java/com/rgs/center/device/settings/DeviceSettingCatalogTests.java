package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingType;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceSettingCatalogTests {

    private final DeviceSettingCatalog catalog = new DeviceSettingCatalog();

    @Test
    void exposesOnlyRuntimeConnectionSettings() {
        assertEquals(36, catalog.definitions().size());
        assertEquals(
                SettingType.STRING,
                catalog.require(SettingsService.SCALES_PORT).type()
        );
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> catalog.require("device.scales.poll-interval-millis")
        );
    }

    @Test
    void validatesComPortsAndNumericRanges() {
        var port = catalog.require(SettingsService.SCALES_PORT);
        var timeout = catalog.require(
                SettingsService.SCALES_RESPONSE_TIMEOUT_MILLIS
        );

        assertEquals("COM12", catalog.convertAndValidate(port, " com12 "));
        assertEquals(5000L, catalog.convertAndValidate(timeout, 5000));
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.convertAndValidate(port, "ttyUSB0")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.convertAndValidate(timeout, 0)
        );
    }

    @Test
    void rejectsInvalidIpv4Address() {
        var definition = catalog.require(SettingsService.CAMERA_IP);

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.convertAndValidate(definition, "192.168.1.999")
        );
    }
}
