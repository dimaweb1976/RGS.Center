package com.rgs.center.barcodescanner;

/** Сканер отвечает, но допустимый штрих-код не получен до тайм-аута. */
public final class BarcodeScannerNoReadException
        extends BarcodeScannerException {

    public BarcodeScannerNoReadException(String message) {
        super(message, null, false);
    }
}
