package com.rgs.center.gammapro;

import com.rgs.center.objects.Device;

import java.time.LocalDateTime;

/** Строка Gamma-PRO с транспортным, а не протокольным статусом. */
public record GammaProDeviceView(
        String name,
        String address,
        GammaProConnectionStatus connectionStatus,
        LocalDateTime lastCheck
) {
    /** Создаёт web-модель, сохраняя адрес и время из общей витрины. */
    public static GammaProDeviceView from(
            Device device,
            GammaProConnectionStatus connectionStatus
    ) {
        return new GammaProDeviceView(
                device.getName(),
                device.getIpAddress(),
                connectionStatus,
                device.getLastCheck()
        );
    }
}
