package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Последний нормализованный протокольный PONG контроллера Nicole. */
public record NicoleEquipmentSnapshot(
        DeviceHealth health,
        Boolean ethernetUp,
        Boolean dhcpLeased,
        Boolean cryoControllerCommunication,
        Boolean cryocoolerStarted,
        Boolean advancedMode,
        Integer currentNitrogenLevel,
        Integer currentNitrogenPressure,
        Integer lowerLevelVoltage,
        Integer upperLevelVoltage,
        Long nitrogenLevelFrequency,
        Instant controllerTime,
        Integer coolerErrorCode,
        Integer warningFlags,
        List<String> warnings,
        Instant pongReceivedAt
) {
    public NicoleEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
