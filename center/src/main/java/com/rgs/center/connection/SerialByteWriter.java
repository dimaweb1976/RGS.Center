package com.rgs.center.connection;

public final class SerialByteWriter
        implements DeviceWriter<SerialDeviceConnection, byte[]> {

    @Override
    public void write(SerialDeviceConnection connection, byte[] value) {
        connection.write(value);
    }
}
