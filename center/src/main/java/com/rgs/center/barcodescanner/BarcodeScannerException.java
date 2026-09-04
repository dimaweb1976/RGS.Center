package com.rgs.center.barcodescanner;

/**
 * Прикладная ошибка настройки или чтения сканера штрих-кодов.
 */
public class BarcodeScannerException extends RuntimeException {

    private final boolean connectionFailure;

    /**
     * @param message описание ошибки
     */
    public BarcodeScannerException(String message) {
        this(message, null, true);
    }

    /**
     * @param message описание ошибки
     * @param cause исходная ошибка периферийного транспорта
     */
    public BarcodeScannerException(String message, Throwable cause) {
        this(message, cause, true);
    }

    /**
     * @param message описание ошибки
     * @param cause исходная ошибка либо {@code null}
     * @param connectionFailure считать ли устройство недоступным
     */
    public BarcodeScannerException(
            String message,
            Throwable cause,
            boolean connectionFailure
    ) {
        super(message, cause);
        this.connectionFailure = connectionFailure;
    }

    /** @return связана ли ошибка с потерей связи со сканером */
    public boolean isConnectionFailure() {
        return connectionFailure;
    }
}
