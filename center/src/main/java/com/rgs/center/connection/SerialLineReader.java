package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Синхронный reader одной строки из COM-порта.
 *
 * <p>Сохранён как простая реализация {@link DeviceReader}; основной
 * асинхронный периферийный конвейер использует {@code LineFrameDecoder}.</p>
 */
public final class SerialLineReader
        implements DeviceReader<SerialDeviceConnection, String> {

    /** Максимальное количество полезных байтов до CR/LF. */
    private final int maxLength;

    /**
     * @param maxLength максимальная длина строки
     */
    public SerialLineReader(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("Maximum length must be positive");
        }
        this.maxLength = maxLength;
    }

    /**
     * Читает одну непустую строку до CR или LF.
     *
     * @param connection открытое последовательное соединение
     * @return строка без завершающих CR/LF
     */
    @Override
    public String read(SerialDeviceConnection connection) {
        byte[] buffer = new byte[maxLength];
        int length = 0;

        try {
            InputStream input = connection.inputStream();
            while (length < maxLength) {
                int nextByte = input.read();
                if (nextByte < 0) {
                    throw new PeripheralCommunicationException(
                            "Data read timed out on port "
                            + connection.portName()
                    );
                }
                if (nextByte == '\r' || nextByte == '\n') {
                    // Пустые разделители перед полезными данными игнорируются.
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
            throw new PeripheralCommunicationException(
                    "Cannot read data from port " + connection.portName(),
                    exception
            );
        }

        throw new PeripheralCommunicationException(
                "Received data exceeds maximum length of " + maxLength
        );
    }
}
