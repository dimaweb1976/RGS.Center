package com.rgs.center.service;

/**
 * Прикладная ошибка настройки или чтения сканера штрих-кодов.
 */
public class BarcodeScannerException extends RuntimeException {

    /**
     * @param message описание ошибки
     */
    public BarcodeScannerException(String message) {
        super(message);
    }

    /**
     * @param message описание ошибки
     * @param cause исходная ошибка периферийного транспорта
     */
    public BarcodeScannerException(String message, Throwable cause) {
        super(message, cause);
    }
}
