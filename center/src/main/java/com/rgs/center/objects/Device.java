package com.rgs.center.objects;

import java.time.LocalDateTime;

public class Device {

    private final String name;
    private final String ipAddress;

    private volatile DeviceStatus status;
    private volatile LocalDateTime lastCheck;

    public Device(String name, String ipAddress) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.status = DeviceStatus.OFFLINE;
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastCheck() {
        return lastCheck;
    }

    public void setLastCheck(LocalDateTime lastCheck) {
        this.lastCheck = lastCheck;
    }
}
