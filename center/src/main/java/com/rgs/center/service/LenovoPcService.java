package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Independently monitors the operator LenovoPC over the peer-to-peer LAN.
 *
 * <p>The computer runs the operator browser, modelling software and the
 * Remote Desktop client for NuvoPC. Its availability is monitored separately
 * from the Dobot controller and process equipment.</p>
 */
@Service
public class LenovoPcService extends AbstractNetworkDeviceService {

    /**
     * @param deviceViewService converts current data to the web-table row
     * @param pingService common queued LAN ping implementation
     */
    public LenovoPcService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(DeviceCode.LENOVO_PC, deviceViewService, pingService);
    }

    /** Runs one non-overlapping LenovoPC availability check. */
    @Scheduled(
            fixedDelayString = "${rgs.device.lenovo-pc.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.lenovo-pc.poll-initial-delay-millis:0}"
    )
    public void poll() {
        pollQueued();
    }

    /** @return last LenovoPC state stored in the current-data showcase */
    public Device getLenovoPc() {
        return currentDevice();
    }
}
