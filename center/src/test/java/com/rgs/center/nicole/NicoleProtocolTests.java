package com.rgs.center.nicole;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NicoleProtocolTests {

    @Test
    void buildsReferencePingFrame() {
        assertArrayEquals(
                new byte[]{
                        (byte) 0xC0, 0x01, 0x00, 0x00,
                        (byte) 0xDC, 0x5A, (byte) 0xC1
                },
                NicoleProtocol.pingFrame()
        );
    }

    @Test
    void decodesAllThirteenPongWords() {
        int[] words = {
                5 | (1 << 8) | (1 << 9) | (1 << 10),
                0b11,
                10,
                20,
                30,
                40,
                0x5678,
                0x1234,
                14 | (25 << 8),
                31 | (17 << 8),
                8 | (26 << 8),
                0x1234,
                0x8201
        };

        NicoleSnapshot snapshot = NicoleProtocol.decodePong(
                NicoleProtocol.encodeFrame(
                        NicoleProtocol.ACK_PING,
                        payload(words)
                )
        );

        assertEquals(5, snapshot.resetCause());
        assertTrue(snapshot.ethernetUp());
        assertTrue(snapshot.dhcpLeased());
        assertTrue(snapshot.cryoControllerCommunication());
        assertTrue(snapshot.cryocoolerStarted());
        assertTrue(snapshot.advancedMode());
        assertEquals(10, snapshot.currentNitrogenLevel());
        assertEquals(20, snapshot.currentNitrogenPressure());
        assertEquals(30, snapshot.lowerLevelVoltage());
        assertEquals(40, snapshot.upperLevelVoltage());
        assertEquals(0x12345678L, snapshot.nitrogenLevelFrequency());
        assertEquals(
                LocalDateTime.of(2026, 8, 17, 14, 25, 31),
                snapshot.controllerTime()
        );
        assertEquals(0x1234, snapshot.coolerErrorCode());
        assertEquals(0x8201, snapshot.warningFlags());
    }

    @Test
    void rejectsFrameWithInvalidCrc() {
        byte[] frame = NicoleProtocol.encodeFrame(
                NicoleProtocol.ACK_PING,
                payload(new int[13])
        );
        frame[2] ^= 0x01;

        assertThrows(
                NicoleProtocolException.class,
                () -> NicoleProtocol.decodePong(frame)
        );
    }

    @Test
    void decodesStuffedReservedBytes() {
        int[] words = new int[13];
        words[2] = 0xC1C0;
        words[3] = 0x007F;
        words[8] = 12;
        words[9] = 1 << 8;
        words[10] = 1 | (26 << 8);

        NicoleSnapshot snapshot = NicoleProtocol.decodePong(
                NicoleProtocol.encodeFrame(
                        NicoleProtocol.ACK_PING,
                        payload(words)
                )
        );

        assertEquals(0xC1C0, snapshot.currentNitrogenLevel());
        assertEquals(0x007F, snapshot.currentNitrogenPressure());
        assertFalse(snapshot.cryocoolerStarted());
    }

    static byte[] pongFrame() {
        return pongFrame(1, 0, 1);
    }

    static byte[] healthyPongFrame() {
        return pongFrame(1, 0, 0);
    }

    private static byte[] pongFrame(
            int coolerStatus,
            int coolerError,
            int warningFlags
    ) {
        int[] words = {
                (1 << 8) | (1 << 10),
                coolerStatus,
                111,
                222,
                333,
                444,
                0x5678,
                0x1234,
                12 | (34 << 8),
                56 | (15 << 8),
                8 | (26 << 8),
                coolerError,
                warningFlags
        };
        return NicoleProtocol.encodeFrame(NicoleProtocol.ACK_PING, payload(words));
    }

    private static byte[] payload(int[] words) {
        ByteBuffer buffer = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : words) {
            buffer.putShort((short) word);
        }
        return buffer.array();
    }
}
