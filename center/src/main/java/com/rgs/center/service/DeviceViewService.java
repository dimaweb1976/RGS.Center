package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Преобразует настройки и витрину в модель, пригодную для web-страницы.
 */
@Service
public class DeviceViewService {

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;

    /**
     * @param settingsService источник адресов устройств
     * @param currentDataService витрина статусов
     */
    public DeviceViewService(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
    }

    /**
     * @param deviceCode код устройства
     * @return новый снимок модели отображения
     */
    public Device getDevice(DeviceCode deviceCode) {
        String address = settingsService.getValue(
                deviceCode.getIpSettingCode(),
                String.class
        );
        if (deviceCode.getPortSettingCode() != null) {
            long port = settingsService.getValue(
                    deviceCode.getPortSettingCode(),
                    Long.class
            );
            address = address + ":" + port;
        }
        Device device = new Device(deviceCode.getDisplayName(), address);
        boolean pinged = currentDataService.getValue(
                deviceCode,
                CurrentDataService.PINGED,
                Boolean.class
        );
        long lastRequest = currentDataService.getValue(
                deviceCode,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        );
        long lastResponse = currentDataService.getValue(
                deviceCode,
                CurrentDataService.INTERACTION_LASTDATE_OUT,
                Long.class
        );
        long lastCheck = Math.max(lastRequest, lastResponse);

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
