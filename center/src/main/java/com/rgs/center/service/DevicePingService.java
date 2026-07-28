package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DevicePingService {

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;
    private final ReachabilityChecker reachabilityChecker;

    public DevicePingService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            ReachabilityChecker reachabilityChecker
    ) {
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
        this.reachabilityChecker = reachabilityChecker;
    }

    public boolean pingDevice(DeviceCode device) {
        String address = settingsService.getValue(
                device.getIpSettingCode(),
                String.class
        );
        int timeoutMillis = Math.toIntExact(settingsService.getValue(
                SettingsService.PING_TIMEOUT_MILLIS,
                Long.class
        ));

        long sentAt = Instant.now().getEpochSecond();
        currentDataService.setValue(
                device,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                sentAt
        );

        boolean pinged = !address.isBlank()
                && reachabilityChecker.isReachable(address, timeoutMillis);
        currentDataService.setValue(
                device,
                CurrentDataService.PINGED,
                pinged
        );

        if (pinged) {
            currentDataService.setValue(
                    device,
                    CurrentDataService.INTERACTION_LASTDATE_OUT,
                    Instant.now().getEpochSecond()
            );
        }

        return pinged;
    }
}
