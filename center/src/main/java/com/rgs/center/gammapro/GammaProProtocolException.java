package com.rgs.center.gammapro;

/** Ошибка формата, выполнения или ожидания команды Gamma-PRO. */
public class GammaProProtocolException extends RuntimeException {

    public GammaProProtocolException(String message) {
        super(message);
    }

    public GammaProProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
