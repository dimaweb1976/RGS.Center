package com.rgs.center.device.settings;

/** Raised when a browser submits a stale optimistic-lock version. */
public final class DeviceSettingsConflictException extends RuntimeException {

    public DeviceSettingsConflictException(String settingId) {
        super("Setting was changed by another user: " + settingId);
    }
}
