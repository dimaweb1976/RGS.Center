package com.rgs.center.shp;

import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Service;

/** Рассчитывает READY/ALERT для последнего достоверного снимка SHP. */
@Service
public class ShpStatusEvaluator {

    private final SettingsService settingsService;

    /** @param settingsService источник изменяемых порогов SHP */
    public ShpStatusEvaluator(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Формирует модель страницы и применяет строгие условия {@code < threshold}.
     * Равенство порогу уже считается ALERT.
     *
     * @param connected подтверждена ли текущая связь с SHP
     * @param measurements последний полный снимок показаний
     * @return готовая модель статуса
     */
    public ShpStatusView evaluate(
            boolean connected,
            ShpMeasurements measurements
    ) {
        if (!connected || measurements == null || !hasFiniteValues(measurements)) {
            return ShpStatusView.noData();
        }

        double maximumChargeVoltage = settingsService.getValue(
                SettingsService.SHP_READY_MAX_CHARGE_OUTPUT_VOLTAGE,
                Double.class
        );
        double maximumLeakageCurrent = settingsService.getValue(
                SettingsService.SHP_READY_MAX_LEAKAGE_CURRENT,
                Double.class
        );
        return evaluate(
                connected,
                measurements,
                maximumChargeVoltage,
                maximumLeakageCurrent
        );
    }

    /** Внутренняя чистая функция оставляет граничные условия тестируемыми. */
    ShpStatusView evaluate(
            boolean connected,
            ShpMeasurements measurements,
            double maximumChargeVoltage,
            double maximumLeakageCurrent
    ) {
        if (!connected || measurements == null || !hasFiniteValues(measurements)) {
            return ShpStatusView.noData();
        }
        boolean ready = measurements.chargeOutputVoltage() < maximumChargeVoltage
                && measurements.detectorCurrent() < maximumLeakageCurrent;

        return new ShpStatusView(
                measurements.ambientTemperatureCelsius(),
                measurements.humidityPercent(),
                measurements.atmosphericPressureHpa(),
                measurements.detectorCurrent(),
                measurements.chargeOutputVoltage(),
                ready ? ShpReadinessStatus.READY : ShpReadinessStatus.ALERT
        );
    }

    /** Проверяет значения, необходимые для вывода и расчёта статуса. */
    private boolean hasFiniteValues(ShpMeasurements measurements) {
        return isFinite(measurements.ambientTemperatureCelsius())
                && isFinite(measurements.humidityPercent())
                && isFinite(measurements.atmosphericPressureHpa())
                && isFinite(measurements.detectorCurrent())
                && isFinite(measurements.chargeOutputVoltage());
    }

    /** @return задано ли конечное числовое значение */
    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
