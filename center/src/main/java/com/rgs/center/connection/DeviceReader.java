package com.rgs.center.connection;

@FunctionalInterface
public interface DeviceReader<C extends DeviceConnection, R> {

    R read(C connection);
}
