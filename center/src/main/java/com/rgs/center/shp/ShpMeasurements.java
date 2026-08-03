package com.rgs.center.shp;

/**
 * Последние показания датчиков предусилителя SHP.
 *
 * @param positiveSupplyVoltage положительное питание модуля, В
 * @param negativeSupplyVoltage отрицательное питание модуля, В
 * @param detectorTemperatureCelsius температура детектора, °C
 * @param chargeOutputVoltage напряжение на выходе зарядовой цепи, В
 * @param detectorCurrent ток детектора
 * @param analogOutputVoltage постоянное напряжение аналогового выхода, В
 * @param feedbackResistance значение резистора обратной связи
 * @param receivedAtEpochSeconds время завершения цикла чтения
 */
public record ShpMeasurements(
        Double positiveSupplyVoltage,
        Double negativeSupplyVoltage,
        Double detectorTemperatureCelsius,
        Double chargeOutputVoltage,
        Double detectorCurrent,
        Double analogOutputVoltage,
        Integer feedbackResistance,
        long receivedAtEpochSeconds
) {
}
