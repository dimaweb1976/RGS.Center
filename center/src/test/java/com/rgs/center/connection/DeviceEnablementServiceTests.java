package com.rgs.center.connection;

import com.rgs.center.objects.DeviceCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceEnablementServiceTests {

    @Test
    void enablesDevicesByDefault() {
        DeviceEnablementService service = new DeviceEnablementService(
                new MockEnvironment()
        );

        for (DeviceCode device : DeviceCode.values()) {
            assertTrue(service.isEnabled(device));
        }
    }

    @Test
    void readsDeviceSpecificFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("rgs.device.gdrm.enabled", "false")
                .withProperty("rgs.device.manipulator.enabled", "false");
        DeviceEnablementService service =
                new DeviceEnablementService(environment);

        assertFalse(service.isEnabled(DeviceCode.GDRM));
        assertFalse(service.isEnabled(DeviceCode.ROBOTIC_MANIPULATOR));
        assertTrue(service.isEnabled(DeviceCode.CAMERA));
    }
}
