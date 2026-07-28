package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class DeviceViewService {

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;

    public DeviceViewService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
    }

    public Device getDevice(DeviceCode deviceCode) {
        String address = settingsService.getValue(
                deviceCode.getIpSettingCode(),
                String.class
        );
        Device device = new Device(deviceCode.getDisplayName(), address);
        boolean pinged = currentDataService.getValue(
                deviceCode,
                CurrentDataService.PINGED,
                Boolean.class
        );
        long lastCheck = currentDataService.getValue(
                deviceCode,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        );

        device.setStatus(pinged ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE);
        if (lastCheck > 0) {
            device.setLastCheck(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(lastCheck),
                    ZoneId.systemDefault()
            ));
        }
        return device;
    }
}
