package com.rgs.center.service;

import com.rgs.center.connection.SerialDeviceConnection;
import com.rgs.center.connection.SerialLineReader;
import org.springframework.stereotype.Component;

@Component
public class JSerialCommBarcodeScannerPort implements BarcodeScannerPort {

    @Override
    public String readCode(
            String portName,
            int baudRate,
            int timeoutMillis,
            int maxCodeLength
    ) {
        SerialDeviceConnection connection = new SerialDeviceConnection(
                portName,
                baudRate,
                timeoutMillis
        );
        try {
            connection.open();
            connection.clearInput();
            return new SerialLineReader(maxCodeLength).read(connection);
        } finally {
            connection.close();
        }
    }
}
