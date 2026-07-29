package com.rgs.center.peripheral;

/**
 * Неизменяемый диагностический снимок worker внешнего устройства.
 *
 * @param deviceId постоянный идентификатор устройства
 * @param state текущее состояние жизненного цикла
 * @param commandQueueDepth число ожидающих отправки команд
 * @param receiveBufferBytes число необработанных входящих байтов
 * @param frameQueueDepth число готовых кадров
 * @param bytesReceived всего принято байтов после запуска объекта
 * @param bytesSent всего передано байтов после запуска объекта
 * @param framesReceived всего выделено кадров
 * @param framesDropped кадров вытеснено из переполненной очереди
 * @param lastReceiveEpochMillis время последнего приёма
 * @param lastTransmitEpochMillis время последней передачи
 * @param lastError описание последней ошибки
 */
public record PeripheralWorkerSnapshot(
        String deviceId,
        PeripheralWorkerState state,
        int commandQueueDepth,
        int receiveBufferBytes,
        int frameQueueDepth,
        long bytesReceived,
        long bytesSent,
        long framesReceived,
        long framesDropped,
        long lastReceiveEpochMillis,
        long lastTransmitEpochMillis,
        String lastError
) {
}
