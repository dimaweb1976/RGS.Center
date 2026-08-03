package com.rgs.center.shp;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/**
 * Неизменяемый снимок состояния SHP для витрины и будущего endpoint.
 *
 * @param deviceCode машинный код устройства
 * @param portName имя виртуального COM-порта
 * @param connected подтверждён ли обмен ответом SHP
 * @param baudRate скорость UART
 * @param lastInteractionInEpochSeconds время последней отправки команды
 * @param lastInteractionOutEpochSeconds время последнего принятого ответа
 * @param lastError последняя ошибка обмена
 * @param measurements последние полные показания
 * @param communication состояние очередей и RX/TX-потоков
 */
public record ShpSnapshot(
        String deviceCode,
        String portName,
        boolean connected,
        int baudRate,
        long lastInteractionInEpochSeconds,
        long lastInteractionOutEpochSeconds,
        String lastError,
        ShpMeasurements measurements,
        PeripheralWorkerSnapshot communication
) {
}
