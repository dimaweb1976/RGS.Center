package com.rgs.center.connection;

import com.rgs.center.service.BarcodeScannerException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class SerialLineReader
        implements DeviceReader<SerialDeviceConnection, String> {

    private final int maxLength;

    public SerialLineReader(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Maximum length must be positive");
        }
        this.maxLength = maxLength;
    }

    @Override
    public String read(SerialDeviceConnection connection) {
        byte[] buffer = new byte[maxLength];
        int length = 0;

        try {
            InputStream input = connection.inputStream();
            while (length < maxLength) {
                int nextByte = input.read();
                if (nextByte < 0) {
                    throw new BarcodeScannerException(
                            "Data read timed out on port "
                            + connection.portName()
                    );
                }
                if (nextByte == '\r' || nextByte == '\n') {
                    if (length == 0) {
                        continue;
                    }
                    return new String(
                            buffer,
                            0,
                            length,
                            StandardCharsets.UTF_8
                    ).trim();
                }
                buffer[length++] = (byte) nextByte;
            }
        } catch (IOException exception) {
            throw new BarcodeScannerException(
                    "Cannot read data from port " + connection.portName(),
                    exception
            );
        }

        throw new BarcodeScannerException(
                "Received data exceeds maximum length of " + maxLength
        );
    }
}
