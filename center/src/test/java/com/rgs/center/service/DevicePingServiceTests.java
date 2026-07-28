package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevicePingServiceTests {

    private SettingsService settings;
    private CurrentDataService currentData;

    @BeforeEach
    void setUp() {
        settings = new SettingsService();
        settings.initializeDefaults();
        currentData = new CurrentDataService();
        currentData.initializeDevices();
    }

    @Test
    void updatesShowcaseWhenDeviceResponds() {
        DevicePingService service = new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> true
        );

        assertTrue(service.pingDevice(DeviceCode.CAMERA));
        assertTrue(currentData.getValue(
                DeviceCode.CAMERA,
                CurrentDataService.PINGED,
                Boolean.class
        ));
        assertTrue(currentData.getValue(
                DeviceCode.CAMERA,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        ) > 0);
        assertTrue(currentData.getValue(
                DeviceCode.CAMERA,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Long.class
        ) > 0);
    }

    @Test
    void doesNotCallNetworkForUnconfiguredAddress() {
        int[] calls = {0};
        DevicePingService service = new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> {
                    calls[0]++;
                    return true;
                }
        );

        assertFalse(service.pingDevice(DeviceCode.NICOLE));
        assertEquals(0, calls[0]);
        assertFalse(currentData.getValue(
                DeviceCode.NICOLE,
                CurrentDataService.PINGED,
                Boolean.class
        ));
    }
}
