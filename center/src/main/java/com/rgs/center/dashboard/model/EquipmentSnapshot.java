package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Согласованная read-only модель оборудования для будущего dashboard.
 *
 * <p>Каждый вложенный блок сохраняет собственное время наблюдения. Поле
 * {@code collectedAt} означает только момент сборки составного snapshot.</p>
 */
public record EquipmentSnapshot(
        Instant collectedAt,
        List<CameraEquipmentSnapshot> cameras,
        DeviceHealth lenovoPc,
        NicoleEquipmentSnapshot nicole,
        ShpEquipmentSnapshot shp,
        ScalesEquipmentSnapshot scales,
        GammaProEquipmentSnapshot gammaPro,
        BosonEquipmentSnapshot boson,
        BarcodeEquipmentSnapshot barcodeScanner,
        ManipulatorEquipmentSnapshot manipulator
) {
    public EquipmentSnapshot {
        Objects.requireNonNull(collectedAt, "collectedAt");
        cameras = List.copyOf(Objects.requireNonNull(cameras, "cameras"));
        Objects.requireNonNull(lenovoPc, "lenovoPc");
        Objects.requireNonNull(nicole, "nicole");
        Objects.requireNonNull(shp, "shp");
        Objects.requireNonNull(scales, "scales");
        Objects.requireNonNull(gammaPro, "gammaPro");
        Objects.requireNonNull(boson, "boson");
        Objects.requireNonNull(barcodeScanner, "barcodeScanner");
        Objects.requireNonNull(manipulator, "manipulator");
    }

    /**
     * Возвращает общие статусы в стабильном порядке страницы устройств.
     *
     * @return новая неизменяемая сводка без дублирования состояний в модели
     */
    public List<DeviceHealth> healthSummary() {
        List<DeviceHealth> result = new ArrayList<>();
        cameras.stream().map(CameraEquipmentSnapshot::health).forEach(result::add);
        result.add(lenovoPc);
        result.add(nicole.health());
        result.add(boson.health());
        result.add(manipulator.health());
        result.add(barcodeScanner.health());
        result.add(scales.health());
        result.add(shp.health());
        result.add(gammaPro.health());
        return List.copyOf(result);
    }
}
