package com.rgs.center.gdrm;

/**
 * Оперативные параметры, возвращаемые командой {@code t}.
 *
 * @param operatingVoltageVolts рабочее напряжение питания, V
 * @param highVoltageVolts высокое напряжение, V
 * @param temperatureCelsius температура внутри прибора, °C
 * @param relativeHumidityPercent относительная влажность, %
 * @param airPressureHpa давление, hPa
 * @param deviceStatus строковое состояние прибора
 * @param receivedAtEpochSeconds время получения ответа
 */
public record GdrmOperatingData(
        Double operatingVoltageVolts,
        Double highVoltageVolts,
        Double temperatureCelsius,
        Double relativeHumidityPercent,
        Double airPressureHpa,
        String deviceStatus,
        long receivedAtEpochSeconds
) {
}
