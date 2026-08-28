package com.rgs.center.shp;

/**
 * Подготовленная для web-страницы модель показаний и статуса SHP.
 *
 * @param ambientTemperatureCelsius температура окружающей среды, °C
 * @param humidityPercent относительная влажность, %
 * @param atmosphericPressureHpa атмосферное давление, hPa
 * @param leakageCurrent ток утечки
 * @param chargeOutputVoltage напряжение на выходе зарядовой цепи, В
 * @param readiness технологический статус
 */
public record ShpStatusView(
        Double ambientTemperatureCelsius,
        Double humidityPercent,
        Double atmosphericPressureHpa,
        Double leakageCurrent,
        Double chargeOutputVoltage,
        ShpReadinessStatus readiness
) {
    /** @return модель без показаний для отключённого или ещё не опрошенного SHP */
    public static ShpStatusView noData() {
        return new ShpStatusView(
                null,
                null,
                null,
                null,
                null,
                ShpReadinessStatus.NO_DATA
        );
    }
}
