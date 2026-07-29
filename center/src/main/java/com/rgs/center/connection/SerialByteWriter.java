package com.rgs.center.connection;

/**
 * Простой writer массива байтов в открытое последовательное соединение.
 */
public final class SerialByteWriter
        implements DeviceWriter<SerialDeviceConnection, byte[]> {

    /**
     * @param connection открытое COM-соединение
     * @param value передаваемые байты
     */
    @Override
    public void write(SerialDeviceConnection connection, byte[] value) {
        connection.write(value);
    }
}
