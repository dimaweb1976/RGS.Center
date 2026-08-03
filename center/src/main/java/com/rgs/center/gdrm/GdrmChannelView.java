package com.rgs.center.gdrm;

/**
 * Отформатированная строка таблицы измерительных каналов GDRM.
 *
 * @param number номер канала
 * @param countsPerSecond скорость счёта, имп/с
 * @param doseRate мощность дозы, нЗв/ч
 * @param statisticalError относительная статистическая погрешность, %
 */
public record GdrmChannelView(
        int number,
        String countsPerSecond,
        String doseRate,
        String statisticalError
) {
}
