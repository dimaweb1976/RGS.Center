package com.rgs.center.gammapro;

import java.util.List;
import java.time.Instant;

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
 * @param lastCommand последняя переданная в Gamma-PRO команда; это может быть
 *                    как ручная команда, так и периодический heartbeat
 * @param lastResponse последняя принятая строка протокола
 * @param lastError последняя ошибка
 * @param connectionAttempted была ли реальная попытка открыть TCP-соединение
 * @param lastSuccessfulHeartbeatAt время полного GET_TRACTS_NAME=OK текущего сеанса
 * @param businessCommandActive выполняется ли ручная команда или запуск/останов измерения;
 *                              периодический heartbeat не является бизнес-операцией
 * @param responseUncertain тайм-аут/ошибка чтения без подтверждённого ответа;
 *                          ER является ответом программы, а не потерей связи
 * @param measurementDeadlineAt предельное время ожидания измерения без idle-heartbeat
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
        String lastCommand,
        String lastResponse,
        String lastError,
        boolean connectionAttempted,
        Instant lastSuccessfulHeartbeatAt,
        boolean businessCommandActive,
        boolean responseUncertain,
        Instant measurementDeadlineAt
) {
    public GammaProSnapshot {
        tracts = List.copyOf(tracts);
    }

    /** Старые потребители не предоставляют доказательства доступности. */
    public GammaProSnapshot(boolean connected, String host, int port,
            GammaProMeasurementState measurementState, String mcaName,
            String channelName, int presetSeconds, List<String> tracts,
            String lastCommand, String lastResponse, String lastError) {
        this(connected, host, port, measurementState, mcaName, channelName,
                presetSeconds, tracts, lastCommand, lastResponse, lastError,
                false, null, false, true, null);
    }
}
