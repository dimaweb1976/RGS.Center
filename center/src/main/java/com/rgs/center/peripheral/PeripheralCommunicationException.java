package com.rgs.center.peripheral;

/**
 * Ошибка соединения, очереди, буфера или протокола периферийного устройства.
 */
public class PeripheralCommunicationException extends RuntimeException {

    /**
     * Создаёт ошибку с поясняющим сообщением.
     *
     * @param message описание причины
     */
    public PeripheralCommunicationException(String message) {
        super(message);
    }

    /**
     * Создаёт ошибку с исходным исключением транспорта.
     *
     * @param message описание причины
     * @param cause исходная ошибка
     */
    public PeripheralCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
