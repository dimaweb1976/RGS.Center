package com.rgs.center.gdrm;

import java.util.List;

/**
 * Результат команды {@code d} GDRM при выбранном формате данных 9.
 *
 * @param channels четыре канала прибора в порядке 1..4
 * @param weightedDoseRateSvPerHour взвешенная мощность дозы, Sv/h
 * @param weightedRelativeStatisticalError относительная ошибка итогового значения
 * @param accumulatedDoseSv накопленная доза, Sv
 * @param accumulationTimeSeconds время накопления, секунды
 * @param deviceStatus строковое состояние прибора
 * @param receivedAtEpochSeconds время получения ответа
 */
public record GdrmMeasurement(
        List<GdrmChannelMeasurement> channels,
        Double weightedDoseRateSvPerHour,
        Double weightedRelativeStatisticalError,
        Double accumulatedDoseSv,
        Double accumulationTimeSeconds,
        String deviceStatus,
        long receivedAtEpochSeconds
) {

    /** Защищает снимок от изменения переданного списка. */
    public GdrmMeasurement {
        channels = List.copyOf(channels);
    }
}
