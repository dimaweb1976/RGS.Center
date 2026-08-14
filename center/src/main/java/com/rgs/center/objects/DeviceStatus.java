package com.rgs.center.objects;

/** Сетевой статус устройства, отображаемый в web-интерфейсе. */
public enum DeviceStatus {
    /** Последняя проверка доступности успешна. */
    ONLINE,
    /** Последняя проверка доступности неуспешна. */
    OFFLINE,
    /** Device polling and connection are disabled by configuration. */
    DISABLED
}
