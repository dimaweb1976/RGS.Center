package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RoboPlcService {

    private final DeviceViewService deviceViewService;
    private final DevicePingService pingService;

    public RoboPlcService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        this.deviceViewService = deviceViewService;
        this.pingService = pingService;
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.roboplc.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.roboplc.poll-initial-delay-millis:0}"
    )
    public void poll() {
        pingService.pingDevice(DeviceCode.ROBOPLC);
    }

    public Device getRoboPlc() {
        return deviceViewService.getDevice(DeviceCode.ROBOPLC);
    }
}
