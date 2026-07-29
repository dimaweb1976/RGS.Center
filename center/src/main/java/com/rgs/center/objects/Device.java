package com.rgs.center.objects;

import java.time.LocalDateTime;

/**
 * Модель строки устройства для web-представления.
 */
public class Device {

    private final String name;
    private final String ipAddress;

    private volatile DeviceStatus status;
    private volatile LocalDateTime lastCheck;

    /**
     * @param name отображаемое имя
     * @param ipAddress сетевой адрес или строка подключения
     */
    public Device(String name, String ipAddress) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.status = DeviceStatus.OFFLINE;
    }

    /** @return отображаемое имя */
    public String getName() {
        return name;
    }

    /** @return адрес устройства */
    public String getIpAddress() {
        return ipAddress;
    }

    /** @return последний рассчитанный статус */
    public DeviceStatus getStatus() {
        return status;
    }

    /** @param status новый статус */
    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    /** @return время последней проверки */
    public LocalDateTime getLastCheck() {
        return lastCheck;
    }

    /** @param lastCheck время последней проверки */
    public void setLastCheck(LocalDateTime lastCheck) {
        this.lastCheck = lastCheck;
    }
}
