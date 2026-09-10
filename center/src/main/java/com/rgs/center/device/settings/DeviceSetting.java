package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingRecord;
import com.rgs.center.objects.SettingType;

import java.time.Instant;

/** One persisted, typed connection setting exposed to the administration UI. */
public record DeviceSetting(
        String stringId,
        String deviceCode,
        SettingType type,
        Object value,
        long version,
        Instant updatedAt,
        Long updatedBy,
        String updatedByLogin
) {

    /** Converts the persistent representation to the existing in-memory format. */
    public SettingRecord toSettingRecord() {
        return SettingRecord.from(stringId, type, value);
    }
}
