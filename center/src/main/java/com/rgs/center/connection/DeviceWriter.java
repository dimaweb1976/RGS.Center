package com.rgs.center.connection;

/**
 * Передаёт прикладные данные через уже открытое соединение.
 *
 * @param <C> тип соединения
 * @param <T> тип передаваемых данных
 */
@FunctionalInterface
public interface DeviceWriter<C extends DeviceConnection, T> {

    /**
     * Отправляет значение устройству.
     *
     * @param connection открытое соединение с устройством
     * @param value данные или команда для передачи
     */
    void write(C connection, T value);
}
