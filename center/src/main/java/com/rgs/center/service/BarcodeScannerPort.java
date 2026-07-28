package com.rgs.center.service;

public interface BarcodeScannerPort {

    String readCode(
            String portName,
            int baudRate,
            int timeoutMillis,
            int maxCodeLength
    );
}
