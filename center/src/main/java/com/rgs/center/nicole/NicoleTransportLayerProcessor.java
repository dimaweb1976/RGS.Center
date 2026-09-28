package com.rgs.center.nicole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NicoleTransportLayerProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private byte[] abUnpacked;
    public byte[] getUnPacked() { return abUnpacked; }

    /**
     * Расчет CRC16 по алгоритму из спецификации Nicole
     */
    public static int calculateCRC16(byte[] pData, int length) {
        int crc = 0;
        int index = 0;
        while (length-- > 0) {
            crc ^= (pData[index++] & 0xFF);
            for (int i = 8; i != 0; i--) {
                if ((crc & 0x01) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Упаковка пакета для отправки хостом с применением байт-стаффинга
     */
    public byte[] pack(int dataType, byte[] data) {
        if (data == null) data = new byte[0];
        int dataSize = data.length;

        byte[] rawPayload = new byte[2 + dataSize];
        rawPayload[0] = (byte) dataSize;
        rawPayload[1] = (byte) dataType;
        System.arraycopy(data, 0, rawPayload, 2, dataSize);

        int crc = calculateCRC16(rawPayload, rawPayload.length);
        byte crcLow = (byte) (crc & 0xFF);
        byte crcHigh = (byte) ((crc >> 8) & 0xFF);

        byte[] fullPayload = new byte[rawPayload.length + 2];
        System.arraycopy(rawPayload, 0, fullPayload, 0, rawPayload.length);
        fullPayload[rawPayload.length] = crcLow;
        fullPayload[rawPayload.length + 1] = crcHigh;

        int stuffedLen = 0;
        for (byte b : fullPayload) {
            int val = b & 0xFF;
            if (val == 0xC0 || val == 0xC1 || val == 0x7F) {
                stuffedLen += 2;
            } else {
                stuffedLen += 1;
            }
        }

        byte[] framed = new byte[1 + stuffedLen + 1];
        int idx = 0;
        framed[idx++] = (byte) 0xC0;

        for (byte b : fullPayload) {
            int val = b & 0xFF;
            if (val == 0xC0) {
                framed[idx++] = (byte) 0x7F;
                framed[idx++] = (byte) (0xC0 ^ 0x20);
            } else if (val == 0xC1) {
                framed[idx++] = (byte) 0x7F;
                framed[idx++] = (byte) (0xC1 ^ 0x20);
            } else if (val == 0x7F) {
                framed[idx++] = (byte) 0x7F;
                framed[idx++] = (byte) (0x7F ^ 0x20);
            } else {
                framed[idx++] = b;
            }
        }
        framed[idx++] = (byte) 0xC1;

        return framed;
    }

    /**
     * Распаковка и проверка входящего ответа от контроллера
     */
    public int unpack(byte[] abIn) {
        if (abIn == null || abIn.length < 6) return 1;
        if (abIn[0] != (byte) 0xC0) return 2;
        if (abIn[abIn.length - 1] != (byte) 0xC1) return 3;

        byte[] tempDest = new byte[abIn.length];
        int destLen = 0;
        boolean inStuffed = false;

        for (int i = 1; i < abIn.length - 1; i++) {
            byte b = abIn[i];
            if (!inStuffed && b == (byte) 0x7F) {
                inStuffed = true;
            } else if (inStuffed) {
                tempDest[destLen++] = (byte) (b ^ 0x20);
                inStuffed = false;
            } else {
                tempDest[destLen++] = b;
            }
        }

        if (destLen < 3) return 4;

        int dataSize = tempDest[0] & 0xFF;
        if (destLen != 2 + dataSize + 2) {
            return 5;
        }

        byte[] payloadToCheck = new byte[2 + dataSize];
        System.arraycopy(tempDest, 0, payloadToCheck, 0, 2 + dataSize);
        
        int calculatedCrc = calculateCRC16(payloadToCheck, payloadToCheck.length);
        int receivedCrc = (tempDest[2 + dataSize] & 0xFF) | ((tempDest[2 + dataSize + 1] & 0xFF) << 8);

        if (calculatedCrc != receivedCrc) {
            logger.error(String.format("Nicole CRC comparison fail: calc=0x%04X, received=0x%04X", calculatedCrc, receivedCrc));
            return 6;
        }

        abUnpacked = new byte[destLen - 2];
        System.arraycopy(tempDest, 0, abUnpacked, 0, destLen - 2);

        return 0;
    }
}