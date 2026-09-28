package com.rgs.center.dobot.command;

import java.nio.charset.StandardCharsets;

public class TcpBufferDobotCommandLine {
    private final byte[] buffer;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public TcpBufferDobotCommandLine(int capacity) {
        this.buffer = new byte[capacity];
    }

    public synchronized void write(byte[] data, int length) {
        for (int i = 0; i < length; i++) {
            if (size >= buffer.length) {
                head = (head + 1) % buffer.length;
                size--;
            }
            buffer[tail] = data[i];
            tail = (tail + 1) % buffer.length;
            size++;
        }
    }

    public synchronized boolean hasCompletePacket() {
        int current = head;
        for (int i = 0; i < size; i++) {
            if (buffer[current] == (byte) '\n') {
                return true;
            }
            current = (current + 1) % buffer.length;
        }
        return false;
    }

    public synchronized String extractPacket() {
        if (!hasCompletePacket()) {
            return null;
        }

        int packetLength = 0;
        int current = head;
        for (int i = 0; i < size; i++) {
            packetLength++;
            if (buffer[current] == (byte) '\n') {
                break;
            }
            current = (current + 1) % buffer.length;
        }

        byte[] packetBytes = new byte[packetLength];
        for (int i = 0; i < packetLength; i++) {
            packetBytes[i] = buffer[head];
            head = (head + 1) % buffer.length;
            size--;
        }

        return new String(packetBytes, StandardCharsets.UTF_8).trim();
    }

    public synchronized void clear() {
        head = 0;
        tail = 0;
        size = 0;
    }
}