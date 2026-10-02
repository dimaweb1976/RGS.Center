package com.rgs.center.ups;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsTransportLayerProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private byte[] abUnpacked;
    public byte[] getUnPacked() { return abUnpacked; }

    /**
     * Расчет стандартной CRC16 для Modbus
     */
    public static int calculateCRC16(byte[] pData, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (pData[i] & 0xFF);
            for (int j = 8; j != 0; j--) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Формирование запроса Modbus (Функция 03: Чтение Holding Registers)
     */
    public byte[] packReadHoldingRegisters(int slaveAddress, int startAddress, int quantity) {
        byte[] frame = new byte[6];
        frame[0] = (byte) slaveAddress;
        frame[1] = (byte) 0x03;
        frame[2] = (byte) ((startAddress >> 8) & 0xFF);
        frame[3] = (byte) (startAddress & 0xFF);
        frame[4] = (byte) ((quantity >> 8) & 0xFF);
        frame[5] = (byte) (quantity & 0xFF);

        int crc = calculateCRC16(frame, frame.length);
        byte[] fullFrame = new byte[frame.length + 2];
        System.arraycopy(frame, 0, fullFrame, 0, frame.length);
        fullFrame[frame.length] = (byte) (crc & 0xFF);
        fullFrame[frame.length + 1] = (byte) ((crc >> 8) & 0xFF);

        return fullFrame;
    }

    /**
     * Распаковка и проверка ответа Modbus
     */
    public int unpack(byte[] abIn) {
        if (abIn == null || abIn.length < 5) return 1;

        int len = abIn.length;
        byte[] pData = new byte[len - 2];
        System.arraycopy(abIn, 0, pData, 0, len - 2);

        int calculatedCrc = calculateCRC16(pData, pData.length);
        int receivedCrc = (abIn[len - 2] & 0xFF) | ((abIn[len - 1] & 0xFF) << 8);

        if (calculatedCrc != receivedCrc) {
            logger.error(String.format("UPS CRC comparison fail: calc=0x%04X, received=0x%04X", calculatedCrc, receivedCrc));
            return 2;
        }

        if ((abIn[1] & 0x80) != 0) {
            logger.error("UPS returned Modbus exception error code: " + (abIn[2] & 0xFF));
            return 3;
        }

        abUnpacked = new byte[len - 2];
        System.arraycopy(abIn, 0, abUnpacked, 0, len - 2);

        return 0;
    }
}