package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BosonServiceTests {

    @Test
    void returnsBosonFromItsOwnService() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();

        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        currentData.setValue(
                DeviceCode.BOSON,
                CurrentDataService.PINGED,
                true
        );
        DevicePingService pingService = new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> true
        );

        BosonService service = new BosonService(
                new DeviceViewService(settings, currentData),
                pingService,
                (mca, channel, enabled, value, type) ->
                        new com.rgs.center.gammapro.BosonHighVoltageSnapshot(
                                mca,
                                channel,
                                enabled,
                                value,
                                type,
                                true,
                                1L,
                                ""
                        )
        );
        Device boson = service.getBoson();

        assertEquals("BOSON", boson.getName());
        assertEquals("192.168.1.16", boson.getIpAddress());
        assertEquals(DeviceStatus.ONLINE, boson.getStatus());
        assertEquals(
                2500,
                service.enableHighVoltage(
                        "BOSON1",
                        "GCD-4020",
                        2500
                ).requestedValue()
        );
    }
}
