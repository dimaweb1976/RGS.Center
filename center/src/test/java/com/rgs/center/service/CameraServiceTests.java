package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraServiceTests {

    @Test
    void returnsOnlyCameras() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();

        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        DevicePingService pingService = new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> true
        );

        CameraService service = new CameraService(
                new DeviceViewService(settings, currentData),
                pingService
        );
        List<Device> devices = service.getCameras();

        assertEquals(List.of("Camera"), devices.stream()
                .map(Device::getName)
                .toList());

        service.poll();
        assertEquals(DeviceStatus.ONLINE, service.getCamera().getStatus());
        assertTrue(service.communicationSnapshot().bytesSent() > 0);
        assertTrue(service.communicationSnapshot().bytesReceived() > 0);
        service.stopCommunication();
    }
}
