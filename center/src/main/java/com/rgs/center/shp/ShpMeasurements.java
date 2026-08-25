package com.rgs.center.shp;

/**
 * Последние показания датчиков предусилителя SHP.
 *
 * @param positiveSupplyVoltage положительное питание модуля, В
 * @param negativeSupplyVoltage отрицательное питание модуля, В
 * @param detectorTemperatureCelsius температура детектора, °C
 * @param chargeOutputVoltage напряжение на выходе зарядовой цепи, В
 * @param detectorCurrent ток детектора, отображаемый как ток утечки
 * @param analogOutputVoltage постоянное напряжение аналогового выхода, В
 * @param ambientTemperatureCelsius температура окружающей среды, °C
 * @param humidityPercent относительная влажность, %
 * @param atmosphericPressureHpa атмосферное давление, hPa
 * @param receivedAtEpochSeconds время завершения цикла чтения
 */
public record ShpMeasurements(
        Double positiveSupplyVoltage,
        Double negativeSupplyVoltage,
        Double detectorTemperatureCelsius,
        Double chargeOutputVoltage,
        Double detectorCurrent,
        Double analogOutputVoltage,
        Double ambientTemperatureCelsius,
        Double humidityPercent,
        Double atmosphericPressureHpa,
        long receivedAtEpochSeconds
) {
}
