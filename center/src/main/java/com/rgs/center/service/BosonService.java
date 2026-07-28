package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BosonService {

    private final DeviceViewService deviceViewService;
    private final DevicePingService pingService;

    public BosonService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        this.deviceViewService = deviceViewService;
        this.pingService = pingService;
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.boson.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.boson.poll-initial-delay-millis:0}"
    )
    public void poll() {
        pingService.pingDevice(DeviceCode.BOSON);
    }

    public Device getBoson() {
        return deviceViewService.getDevice(DeviceCode.BOSON);
    }
}
