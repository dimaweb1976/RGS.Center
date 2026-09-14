package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingType;

import java.time.Instant;

/** Immutable history entry for one device-setting change. */
public record DeviceSettingAudit(
        long id,
        String stringId,
        String deviceCode,
        SettingType type,
        String oldValue,
        String newValue,
        Instant changedAt,
        long changedBy,
        String changedByLogin
) {
}
