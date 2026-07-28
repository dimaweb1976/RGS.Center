package com.rgs.center.connection;

public interface DeviceConnection extends AutoCloseable {

    void open();

    boolean isOpen();

    @Override
    void close();
}
