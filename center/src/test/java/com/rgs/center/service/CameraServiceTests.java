package com.rgs.center.service;

import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void disabledCameraDoesNotExecuteScheduledPing() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        DeviceEnablementService enablement = new DeviceEnablementService(
                new MockEnvironment().withProperty(
                        "rgs.device.camera.enabled",
                        "false"
                )
        );
        CameraService service = new CameraService(
                new DeviceViewService(settings, currentData, enablement),
                new DevicePingService(
                        settings,
                        currentData,
                        (address, timeout) -> true
                )
        );
        service.setDeviceEnablementService(enablement);

        service.poll();

        assertFalse(service.isEnabled());
        assertEquals(DeviceStatus.DISABLED, service.getCamera().getStatus());
        assertEquals(0L, service.communicationSnapshot().bytesSent());
        assertEquals(
                0L,
                currentData.getValue(
                        DeviceCode.CAMERA,
                        CurrentDataService.INTERACTION_LASTDATE_IN,
                        Long.class
                )
        );
    }
}
