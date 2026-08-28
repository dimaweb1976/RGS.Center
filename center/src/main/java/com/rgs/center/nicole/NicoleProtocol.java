package com.rgs.center.nicole;

import java.io.ByteArrayOutputStream;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

/** Кодирование PING и строгий разбор ACK_PING/PONG протокола NICOLE V1.03A. */
public final class NicoleProtocol {

    public static final byte START = (byte) 0xC0;
    public static final byte STOP = (byte) 0xC1;
    public static final byte ESCAPE = 0x7F;
    public static final int PING = 0x00;
    public static final int ACK_PING = 0x10;
    public static final int PONG_DATA_LENGTH = 26;
    public static final int MAX_TRANSPORT_FRAME_LENGTH = 522;

    private static final int ESCAPE_XOR = 0x20;
    private static final byte[] PING_FRAME = encodeFrame(PING, new byte[]{0});

    private NicoleProtocol() {
    }

    /** @return новый массив с эталонным транспортным PING-кадром */
    public static byte[] pingFrame() {
        return Arrays.copyOf(PING_FRAME, PING_FRAME.length);
    }

    /**
     * Проверяет транспортный кадр, CRC16 и преобразует PONG в типизированный снимок.
     *
     * @param transportFrame кадр вместе с C0/C1 и byte stuffing
     * @return проверенные значения ACK_PING
     */
    public static NicoleSnapshot decodePong(byte[] transportFrame) {
        byte[] packet = decodeTransportFrame(transportFrame);
        int dataSize = unsigned(packet[0]);
        if (dataSize != PONG_DATA_LENGTH) {
            throw new NicoleProtocolException(
                    "Unexpected NICOLE PONG data size: " + dataSize
            );
        }
        if (unsigned(packet[1]) != ACK_PING) {
            throw new NicoleProtocolException(String.format(
                    "Unexpected NICOLE response type: 0x%02X",
                    unsigned(packet[1])
            ));
        }

        int nicoleStatus = word(packet, 2);
        int coolerStatus = word(packet, 4);
        long frequency = Integer.toUnsignedLong(word(packet, 14))
                | (Integer.toUnsignedLong(word(packet, 16)) << 16);
        int hoursMinutes = word(packet, 18);
        int secondsDay = word(packet, 20);
        int monthYear = word(packet, 22);

        LocalDateTime controllerTime;
        try {
            controllerTime = LocalDateTime.of(
                    2000 + ((monthYear >>> 8) & 0xFF),
                    monthYear & 0xFF,
                    (secondsDay >>> 8) & 0xFF,
                    hoursMinutes & 0xFF,
                    (hoursMinutes >>> 8) & 0xFF,
                    secondsDay & 0xFF
            );
        } catch (DateTimeException exception) {
            throw new NicoleProtocolException(
                    "Invalid controller date/time in NICOLE PONG",
                    exception
            );
        }

        return new NicoleSnapshot(
                nicoleStatus & 0xFF,
                bit(nicoleStatus, 8),
                bit(nicoleStatus, 9),
                bit(nicoleStatus, 10),
                bit(coolerStatus, 0),
                bit(coolerStatus, 1),
                word(packet, 6),
                word(packet, 8),
                word(packet, 10),
                word(packet, 12),
                frequency,
                controllerTime,
                word(packet, 24),
                word(packet, 26),
                Instant.now()
        );
    }

    /** Формирует транспортный кадр с CRC16 и экранированием служебных байтов. */
    static byte[] encodeFrame(int dataType, byte[] data) {
        if (dataType < 0 || dataType > 0xFF || data.length > 0xFF) {
            throw new IllegalArgumentException("Invalid NICOLE frame fields");
        }
        ByteArrayOutputStream packet = new ByteArrayOutputStream(data.length + 4);
        packet.write(data.length);
        packet.write(dataType);
        packet.writeBytes(data);
        int crc = crc16(packet.toByteArray());
        packet.write(crc & 0xFF);
        packet.write((crc >>> 8) & 0xFF);

        ByteArrayOutputStream frame = new ByteArrayOutputStream(packet.size() * 2 + 2);
        frame.write(START);
        for (byte value : packet.toByteArray()) {
            int unsigned = unsigned(value);
            if (unsigned == unsigned(START)
                    || unsigned == unsigned(STOP)
                    || unsigned == unsigned(ESCAPE)) {
                frame.write(ESCAPE);
                frame.write(unsigned ^ ESCAPE_XOR);
            } else {
                frame.write(unsigned);
            }
        }
        frame.write(STOP);
        return frame.toByteArray();
    }

    /** CRC16 с отражённым полиномом 0x8408 и начальным значением 0. */
    static int crc16(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= unsigned(value);
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8408 : crc >>> 1;
            }
        }
        return crc & 0xFFFF;
    }

    private static byte[] decodeTransportFrame(byte[] frame) {
        if (frame == null || frame.length < 6
                || frame[0] != START || frame[frame.length - 1] != STOP) {
            throw new NicoleProtocolException("Invalid NICOLE frame boundaries");
        }
        ByteArrayOutputStream packet = new ByteArrayOutputStream(frame.length - 2);
        for (int index = 1; index < frame.length - 1; index++) {
            int value = unsigned(frame[index]);
            if (value == unsigned(ESCAPE)) {
                if (++index >= frame.length - 1) {
                    throw new NicoleProtocolException("Dangling NICOLE escape byte");
                }
                value = unsigned(frame[index]) ^ ESCAPE_XOR;
            }
            packet.write(value);
        }

        byte[] decoded = packet.toByteArray();
        if (decoded.length < 4) {
            throw new NicoleProtocolException("NICOLE packet is too short");
        }
        int expectedLength = unsigned(decoded[0]) + 4;
        if (decoded.length != expectedLength) {
            throw new NicoleProtocolException(
                    "NICOLE data size mismatch: expected=" + expectedLength
                            + ", actual=" + decoded.length
            );
        }
        int receivedCrc = unsigned(decoded[decoded.length - 2])
                | (unsigned(decoded[decoded.length - 1]) << 8);
        int expectedCrc = crc16(Arrays.copyOf(decoded, decoded.length - 2));
        if (receivedCrc != expectedCrc) {
            throw new NicoleProtocolException(String.format(
                    "NICOLE CRC mismatch: expected=0x%04X, received=0x%04X",
                    expectedCrc,
                    receivedCrc
            ));
        }
        return decoded;
    }

    private static int word(byte[] packet, int offset) {
        return unsigned(packet[offset]) | (unsigned(packet[offset + 1]) << 8);
    }

    private static boolean bit(int value, int bit) {
        return (value & (1 << bit)) != 0;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
