package com.rgs.center.connection;

import com.fazecast.jSerialComm.SerialPort;
import com.rgs.center.service.BarcodeScannerException;

import java.io.IOException;
import java.io.InputStream;

public final class SerialDeviceConnection implements DeviceConnection {

    private final SerialPort port;
    private final String portName;

    public SerialDeviceConnection(
            String portName,
            int baudRate,
            int timeoutMillis
    ) {
        this.portName = portName;
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(
                baudRate,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                timeoutMillis,
                0
        );
    }

    @Override
    public void open() {
        if (!port.openPort()) {
            throw new BarcodeScannerException(
                    "Cannot open serial port " + portName
            );
        }
    }

    @Override
    public boolean isOpen() {
        return port.isOpen();
    }

    public void clearInput() {
        requireOpen();
        port.flushIOBuffers();
    }

    public InputStream inputStream() {
        requireOpen();
        return port.getInputStream();
    }

    public void write(byte[] value) {
        requireOpen();
        int written = port.writeBytes(value, value.length);
        if (written != value.length) {
            throw new BarcodeScannerException(
                    "Cannot write all data to serial port " + portName
            );
        }
    }

    public String portName() {
        return portName;
    }

    @Override
    public void close() {
        if (port.isOpen()) {
            try {
                port.getInputStream().close();
            } catch (IOException ignored) {
                // The port still has to be closed.
            }
            port.closePort();
        }
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException(
                    "Serial port " + portName + " is not open"
            );
        }
    }
}
