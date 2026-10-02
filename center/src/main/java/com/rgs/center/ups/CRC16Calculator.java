package com.rgs.center.ups;

public class CRC16Calculator {

    // Calculates Modbus CRC-16 checksum for the given byte array
    public static int calculateCRC(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = crc >> 1;
                }
            }
        }
        return crc;
    }
}
