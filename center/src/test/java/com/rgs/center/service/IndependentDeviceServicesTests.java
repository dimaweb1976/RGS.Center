package com.rgs.center.service;

import com.rgs.center.objects.DeviceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndependentDeviceServicesTests {

    @Test
    void nicoleServicePollsNicoleItself() {
        SettingsService settings = settings();
        CurrentDataService currentData = currentData();
        DevicePingService pingService = pingService(settings, currentData);
        NicoleService service = new NicoleService(
                new DeviceViewService(settings, currentData),
                pingService
        );

        service.poll();

        assertEquals(DeviceStatus.ONLINE, service.getNicole().getStatus());
    }

    @Test
    void roboPlcServicePollsRoboPlcItself() {
        SettingsService settings = settings();
        settings.setValue(SettingsService.ROBOPLC_IP, "192.168.1.18");
        CurrentDataService currentData = currentData();
        DevicePingService pingService = pingService(settings, currentData);
        RoboPlcService service = new RoboPlcService(
                new DeviceViewService(settings, currentData),
                pingService
        );

        service.poll();

        assertEquals(DeviceStatus.ONLINE, service.getRoboPlc().getStatus());
    }

    private SettingsService settings() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        settings.setValue(SettingsService.NICOLE_IP, "192.168.1.17");
        return settings;
    }

    private CurrentDataService currentData() {
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        return currentData;
    }

    private DevicePingService pingService(
            SettingsService settings,
            CurrentDataService currentData
    ) {
        return new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> true
        );
    }
}
