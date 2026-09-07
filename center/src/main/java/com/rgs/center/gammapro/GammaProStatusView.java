package com.rgs.center.gammapro;

import java.time.Instant;

/**
 * Данные отдельной таблицы состояния Gamma-PRO.
 *
 * @param connectionStatus фактическое состояние TCP-сокета
 * @param bosonConnectionStatus наличие ожидаемого BOSON-тракта в свежем
 *                              ответе GET_TRACTS_NAME; это не проверка USB-B
 * @param snapshot последний снимок протокола и измерения
 * @param lastUpdatedAt время последнего ответа Gamma-PRO
 * @param bosonLastConfirmedAt время последнего списка трактов с BOSON
 */
public record GammaProStatusView(
        GammaProConnectionStatus connectionStatus,
        BosonConnectionStatus bosonConnectionStatus,
        GammaProSnapshot snapshot,
        Instant lastUpdatedAt,
        Instant bosonLastConfirmedAt
) {
}
