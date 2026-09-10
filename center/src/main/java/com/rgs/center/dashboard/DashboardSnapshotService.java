package com.rgs.center.dashboard;

import com.rgs.center.dashboard.model.DashboardSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Собирает единый снимок Dashboard без обращения к физическим устройствам. */
@Service
public class DashboardSnapshotService {

    private final EquipmentSnapshotAssembler equipmentAssembler;
    private final Clock clock;

    /** Создаёт production-сервис с UTC-часами приложения. */
    @Autowired
    public DashboardSnapshotService(
            EquipmentSnapshotAssembler equipmentAssembler
    ) {
        this(equipmentAssembler, Clock.systemUTC());
    }

    /** Конструктор с управляемыми часами для детерминированных тестов. */
    DashboardSnapshotService(
            EquipmentSnapshotAssembler equipmentAssembler,
            Clock clock
    ) {
        this.equipmentAssembler = Objects.requireNonNull(
                equipmentAssembler,
                "equipmentAssembler"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Читает уже опубликованные состояния всех устройств и агрегирует их.
     *
     * @return неизменяемый снимок одной версии API
     */
    public DashboardSnapshot snapshot() {
        Instant collectedAt = clock.instant();
        return new DashboardSnapshot(
                DashboardSnapshot.CURRENT_SCHEMA_VERSION,
                equipmentAssembler.assemble(collectedAt)
        );
    }
}
