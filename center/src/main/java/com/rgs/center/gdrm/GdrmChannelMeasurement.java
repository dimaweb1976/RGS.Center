package com.rgs.center.gdrm;

/**
 * Измерение одного чувствительного канала GDRM в формате протокола 9.
 *
 * @param countsPerSecond скорость счёта, cps; {@code null} для отключённого канала
 * @param doseRateSvPerHour мощность дозы, Sv/h
 * @param relativeStatisticalError относительная статистическая ошибка
 */
public record GdrmChannelMeasurement(
        Double countsPerSecond,
        Double doseRateSvPerHour,
        Double relativeStatisticalError
) {
}
