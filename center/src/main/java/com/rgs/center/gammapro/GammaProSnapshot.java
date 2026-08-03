package com.rgs.center.gammapro;

import java.util.List;

/**
 * Неизменяемый снимок соединения и текущего измерения Gamma-PRO.
 *
 * @param connected подтверждена ли связь прикладной командой
 * @param host адрес сервера Gamma-PRO
 * @param port TCP-порт сервера
 * @param measurementState состояние набора спектра
 * @param mcaName выбранный анализатор
 * @param channelName выбранный канал
 * @param presetSeconds заданная экспозиция, с
 * @param tracts последний ответ GET_TRACTS_NAME
 * @param lastResponse последняя принятая строка протокола
 * @param lastError последняя ошибка
 */
public record GammaProSnapshot(
        boolean connected,
        String host,
        int port,
        GammaProMeasurementState measurementState,
        String mcaName,
        String channelName,
        int presetSeconds,
        List<String> tracts,
        String lastResponse,
        String lastError
) {
    public GammaProSnapshot {
        tracts = List.copyOf(tracts);
    }
}
