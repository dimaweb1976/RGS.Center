package com.rgs.center.peripheral;

import com.rgs.center.connection.DeviceConnection;

/**
 * Двунаправленный транспорт внешнего периферийного устройства.
 *
 * <p>Интерфейс отделяет worker от конкретной библиотеки COM/TCP. Реализация
 * должна допускать одновременное блокирующее чтение в RX-потоке и запись
 * в TX-потоке.</p>
 */
public interface PeripheralTransport extends DeviceConnection {

    /**
     * Читает очередную порцию входящих байтов.
     *
     * @param buffer буфер назначения
     * @return количество прочитанных байтов; значение {@code <= 0} означает,
     * что за текущий интервал данные не получены
     */
    int read(byte[] buffer);

    /**
     * Полностью отправляет массив байтов устройству.
     *
     * @param value команда или данные для отправки
     */
    void write(byte[] value);

    /**
     * Удаляет ранее накопленные входящие данные транспорта.
     */
    void clearInput();
}
