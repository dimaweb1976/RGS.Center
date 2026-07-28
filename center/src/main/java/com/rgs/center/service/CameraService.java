package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CameraService {

    private final DeviceViewService deviceViewService;
    private final DevicePingService pingService;

    public CameraService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        this.deviceViewService = deviceViewService;
        this.pingService = pingService;
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.camera.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.camera.poll-initial-delay-millis:0}"
    )
    public void poll() {
        pingService.pingDevice(DeviceCode.CAMERA);
    }

    public Device getCamera() {
        return deviceViewService.getDevice(DeviceCode.CAMERA);
    }

    public List<Device> getCameras() {
        return List.of(getCamera());
    }
}
