package com.rgs.center.api.devices;

import com.rgs.center.gammapro.GammaProConnectionStatus;
import com.rgs.center.objects.DeviceStatus;

/** Статусы подключения, публикуемые общей таблицей устройств. */
public enum DeviceConnectionStatus {
    ONLINE,
    OFFLINE,
    DISABLED,
    CONNECTED,
    DISCONNECTED;

    /** Сохраняет существующие статусы устройств, проверяемых прикладным опросом. */
    public static DeviceConnectionStatus from(DeviceStatus status) {
        return valueOf(status.name());
    }

    /** Публикует отдельную семантику фактического сокета Gamma-PRO. */
    public static DeviceConnectionStatus from(
            GammaProConnectionStatus status
    ) {
        return valueOf(status.name());
    }
}
