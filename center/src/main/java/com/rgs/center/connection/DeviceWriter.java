package com.rgs.center.connection;

@FunctionalInterface
public interface DeviceWriter<C extends DeviceConnection, T> {

    void write(C connection, T value);
}
