package com.rgs.center.dashboard.model;

import java.util.Objects;

/**
 * Верхнеуровневый read-only снимок для Thymeleaf, REST и сценария измерения.
 *
 * <p>Версия относится к JSON/consumer-контракту. Момент сборки находится в
 * {@link EquipmentSnapshot#collectedAt()} и не дублируется на этом уровне.</p>
 */
public record DashboardSnapshot(
        int schemaVersion,
        EquipmentSnapshot equipment
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public DashboardSnapshot {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(equipment, "equipment");
    }
}
