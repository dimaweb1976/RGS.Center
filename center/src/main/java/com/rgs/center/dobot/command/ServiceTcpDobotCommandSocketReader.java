package com.rgs.center.dobot.command;

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceTcpDobotCommandSocketReader implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final InputStream inputStream;
    private final TcpBufferDobotCommandLine tcpBuffer;
    private volatile boolean running = true;

    public ServiceTcpDobotCommandSocketReader(InputStream inputStream, TcpBufferDobotCommandLine tcpBuffer) {
        this.inputStream = inputStream;
        this.tcpBuffer = tcpBuffer;
    }

    @Override
    public void run() {
        byte[] readBuffer = new byte[1024];
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (inputStream != null && inputStream.available() > 0) {
                    int bytesRead = inputStream.read(readBuffer);
                    if (bytesRead > 0) {
                        tcpBuffer.write(readBuffer, bytesRead);
                    }
                } else {
                    Thread.sleep(10);
                }
            } catch (Exception ex) {
                logger.error("Error reading from Dobot command stream", ex);
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }
}