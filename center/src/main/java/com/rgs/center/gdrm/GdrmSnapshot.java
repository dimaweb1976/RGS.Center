package com.rgs.center.gdrm;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/**
 * Полный неизменяемый ответ endpoint {@code GET /gdrm}.
 *
 * @param deviceCode машинный код устройства
 * @param portName системное имя COM-порта
 * @param connected подтверждена ли связь ответом на запрос серийного номера
 * @param baudRate определённая скорость обмена
 * @param address однобуквенный адрес прибора
 * @param serialNumber серийный номер прибора
 * @param firmwareVersion версия встроенного ПО
 * @param lastInteractionInEpochSeconds время последней отправленной команды
 * @param lastInteractionOutEpochSeconds время последнего принятого ответа
 * @param lastError последняя ошибка обмена
 * @param measurement последнее измерение команды {@code d}
 * @param operatingData последние оперативные данные команды {@code t}
 * @param communication диагностический снимок очередей и RX/TX-потоков
 */
public record GdrmSnapshot(
        String deviceCode,
        String portName,
        boolean connected,
        int baudRate,
        String address,
        String serialNumber,
        String firmwareVersion,
        long lastInteractionInEpochSeconds,
        long lastInteractionOutEpochSeconds,
        String lastError,
        GdrmMeasurement measurement,
        GdrmOperatingData operatingData,
        PeripheralWorkerSnapshot communication
) {
}
