package com.rgs.center.device.settings;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks saved connection settings that have not yet been applied to a device. */
@Component
public class DeviceSettingsApplyTracker {

    private final Set<String> pendingDevices = ConcurrentHashMap.newKeySet();

    public void markPending(String deviceCode) {
        pendingDevices.add(deviceCode);
    }

    public void markApplied(String deviceCode) {
        pendingDevices.remove(deviceCode);
    }

    public boolean isPending(String deviceCode) {
        return pendingDevices.contains(deviceCode);
    }
}
