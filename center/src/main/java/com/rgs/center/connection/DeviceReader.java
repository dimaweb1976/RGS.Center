package com.rgs.center.connection;

/**
 * Читает прикладные данные через уже открытое соединение.
 *
 * @param <C> тип соединения
 * @param <R> тип результата чтения
 */
@FunctionalInterface
public interface DeviceReader<C extends DeviceConnection, R> {

    /**
     * Получает данные через переданное соединение.
     *
     * @param connection открытое соединение с устройством
     * @return прочитанные и декодированные данные
     */
    R read(C connection);
}
