package com.rgs.center.nicole;

/** Ошибка структуры, контрольной суммы или содержимого кадра NICOLE. */
public class NicoleProtocolException extends RuntimeException {

    /** @param message диагностическое описание нарушения протокола */
    public NicoleProtocolException(String message) {
        super(message);
    }

    /**
     * @param message диагностическое описание нарушения протокола
     * @param cause исходная ошибка разбора значения
     */
    public NicoleProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
