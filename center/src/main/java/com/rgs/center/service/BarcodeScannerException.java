package com.rgs.center.service;

public class BarcodeScannerException extends RuntimeException {

    public BarcodeScannerException(String message) {
        super(message);
    }

    public BarcodeScannerException(String message, Throwable cause) {
        super(message, cause);
    }
}
