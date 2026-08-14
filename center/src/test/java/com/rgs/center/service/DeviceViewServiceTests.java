package com.rgs.center.service;

import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceViewServiceTests {

    @Test
    void displaysDisabledInsteadOfOffline() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        DeviceEnablementService enablement = new DeviceEnablementService(
                new MockEnvironment().withProperty(
                        "rgs.device.gdrm.enabled",
                        "false"
                )
        );
        DeviceViewService service = new DeviceViewService(
                settings,
                currentData,
                enablement
        );

        Device device = service.getDevice(DeviceCode.GDRM);

        assertEquals(DeviceStatus.DISABLED, device.getStatus());
        assertEquals(null, device.getLastCheck());
    }
}
