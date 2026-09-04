package com.rgs.center.barcodescanner;

/** Оператор отменил выполняющееся чтение штрих-кода. */
public final class BarcodeScannerCancelledException
        extends BarcodeScannerException {

    public BarcodeScannerCancelledException(String message, Throwable cause) {
        super(message, cause, false);
    }
}
