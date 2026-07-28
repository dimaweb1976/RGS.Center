package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NicoleService {

    private final DeviceViewService deviceViewService;
    private final DevicePingService pingService;

    public NicoleService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        this.deviceViewService = deviceViewService;
        this.pingService = pingService;
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.nicole.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.nicole.poll-initial-delay-millis:0}"
    )
    public void poll() {
        pingService.pingDevice(DeviceCode.NICOLE);
    }

    public Device getNicole() {
        return deviceViewService.getDevice(DeviceCode.NICOLE);
    }
}
